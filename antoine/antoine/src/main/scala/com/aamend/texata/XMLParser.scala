package com.aamend.texata

import java.text.SimpleDateFormat

import com.aamend.texata.io.CDETSInputFormat
import com.aamend.texata.model.Model.{DefectApplication, Defect}
import org.apache.hadoop.io.{LongWritable, Text}
import org.apache.spark.rdd.RDD
import org.apache.spark.{SparkConf, SparkContext}
import org.xml.sax.SAXParseException

import scala.xml.{Elem, XML}

object XMLParser {

  def main(args: Array[String]) : Unit = {

    val inputDir = "texata/xml"
    val outputDir = "texata/defects-lights"
    val outputDefectDir = "texata/defects-lights-objects"
    val partitions = 20

    val sc = new SparkContext(new SparkConf().setAppName(this.getClass.toString))

    // Read XML records using CDETS Input Format
    // This makes sure we split records from a same file
    val inputXml: RDD[String] = sc.newAPIHadoopFile(inputDir, classOf[CDETSInputFormat], classOf[LongWritable], classOf[Text]).map(_._2.toString)

    // Parse XML and extract the following object
    val inputDefects: RDD[Option[Defect]] = inputXml.map({case strXml =>

      try {

        val xml: Elem = XML.loadString(strXml)

        val defectId: String = (xml \ "Defect" \ "@id").toString()

        val fieldMap: Map[String, Set[String]] = xml.descendant.filter(_.label == "Field").map({ case field =>
          (field.attribute("name").getOrElse("").toString, field.text)
        }).groupBy(_._1).map(t => (t._1, t._2.map(_._2).toSet)).filter({case (key, values) =>
          key.length > 0 && values.nonEmpty
        })

        Some(Defect(defectId, fieldMap))

      } catch {
        case e: Throwable => None: Option[Defect]
        case e: SAXParseException => None: Option[Defect]
      }
    })

    // Save this Defect object back to HDFS
    inputDefects.filter(_.isDefined).map(_.get).repartition(partitions).saveAsObjectFile(outputDir)
    inputDefects.filter(_.isDefined).map(_.get).count()
    // Long = 91048
    // Good, enough data!

    // Get a better version now

    val defects = sc.objectFile[Defect](outputDir)

    val productsId = defects.flatMap(d => d.metadata.getOrElse("Component", Set(""))).filter(_.length > 0).distinct().zipWithIndex().mapValues(l => l + 1L).collectAsMap()
    // Int = 1249, at least better than Product
    // Class: 9 distinct
    // Product: 5 distinct
    // Component: 1249 distinct
    // Projects: 24 distinct

    // Let's use Component for the time beeing


    val sProductsId = sc.broadcast(productsId)

    val applicationDefects: RDD[(String, DefectApplication)] = defects.mapPartitions({case it: Iterator[Defect] =>

      val dic = sProductsId.value
      for(defect <- it) yield {

        val createdDate = defect.metadata.getOrElse("New-on", Set("")).head
        var closedDate = defect.metadata.getOrElse("Closed-on", Set("")).head

        if(closedDate.length == 0){
          closedDate = defect.metadata.getOrElse("Resolved-on", Set("")).head
        }

        if(closedDate.length == 0){
          closedDate = defect.metadata.getOrElse("Sys-Last-Updated", Set("")).head
        }

        val versions = defect.metadata.getOrElse("Version", Set("")) // Observed in
        val status = defect.metadata.getOrElse("Status", Set("")).mkString(",")
        val releases = defect.metadata.getOrElse("Integrated-releases", Set(""))
        val product = defect.metadata.getOrElse("Component", Set("")).head
        val productId = dic.getOrElse(product, 0L)
        val severities = defect.metadata.getOrElse("Severity", Set("")).toList.filter(_.matches("\\d+")).map(_.toInt).sorted
        val severity = if (severities.isEmpty) 0 else severities.head

        val sdf = new SimpleDateFormat("MM/dd/yy HH:mm:ss")

        var createdTime = 0L
        try {
          createdTime = sdf.parse(createdDate).getTime
        } catch {
          case e: Throwable =>
        }

        var closedTime = 0L
        try {
          closedTime = sdf.parse(closedDate).getTime
        } catch {
          case e: Throwable =>
        }

        (status, DefectApplication(
          defect.defectId,
          productId,
          product,
          createdTime,
          closedTime,
          severity,
          versions.toArray,
          releases.toArray
        ))
      }

    }).filter(t => t._2.fromDate > 0L && t._2.toDate > 0L && t._2.appId > 0L)

    applicationDefects.count()
    //Long = 90213

    applicationDefects.filter(t => t._1.split(",").exists(s => Set("C", "D", "R", "U").contains(s))).count()
    //Long = 71325

    applicationDefects.filter(t => t._1.split(",").exists(s => Set("C", "D", "R", "U").contains(s))).map(_._2).saveAsObjectFile(outputDefectDir)
  }

}
