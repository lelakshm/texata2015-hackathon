package com.aamend.texata

import java.text.SimpleDateFormat

import com.aamend.texata.graph.ContagionPageRank
import com.aamend.texata.io.CDETSInputFormat
import com.aamend.texata.model.Model.{ApplicationEdge, DefectApplication, Defect}
import com.typesafe.config.ConfigFactory
import org.apache.hadoop.io.{Text, LongWritable}
import org.apache.spark.graphx.{Graph, Edge}
import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.rdd.RDD
import org.joda.time.DateTime

import scala.xml.{XML, Elem}

object Contagion {

  def main(args: Array[String]) = {

    val config = ConfigFactory.load()

    val inputDir = config.getString("texata.cdets.xml.input.dir")
    val outputDir = config.getString("texata.cdets.contagion.output.dir")
    val partitions = config.getInt("texata.cdets.xml.repartition")

    val sc = new SparkContext(new SparkConf().setAppName(this.getClass.toString))

    val defects = parseCDETS(sc, inputDir, partitions)
    val (appDefects, appContagions) = buildContagion(sc, defects)
    val (contagionNodes, contagionEdges) = computeRisk(sc, appDefects, appContagions)

    contagionNodes.saveAsTextFile(s"$outputDir/nodes")
    contagionEdges.saveAsTextFile(s"$outputDir/edges")

  }


  def parseCDETS(sc: SparkContext, inputDir: String, partitions: Int): RDD[Defect] = {

    // Read XML records using CDETS Input Format
    // This makes sure we split records from a same file
    val inputXml: RDD[String] = sc.newAPIHadoopFile(inputDir, classOf[CDETSInputFormat], classOf[LongWritable], classOf[Text]).map(_._2.toString)

    // Parse XML and extract defect object
     inputXml.map({case strXml =>

      val xml: Elem = XML.loadString(strXml)

      val defectId: String = (xml \ "Defect" \ "@id").toString()

      val fieldMap: Map[String, Set[String]] = xml.descendant.filter(_.label == "Field").map({ case field =>
        (field.attribute("name").getOrElse("").toString, field.text)
      }).groupBy(_._1).map(t => (t._1, t._2.map(_._2).toSet)).filter({case (key, values) =>
        key.length > 0 && values.nonEmpty
      })

//      val auditList: Array[(String, Map[String, String])] = xml.descendant.filter(_.label == "AuditTrail").map({ case audit =>
//        val auditDate = (audit \ "@id").toString()
//        val map = audit.child.filter(_.label == "Field").map(n => (n.attribute("name").getOrElse("").toString, n.text)).toMap
//        (auditDate, map)
//      }).filter({case (date, map) =>
//        date.length > 0 && map.nonEmpty
//      }).toArray

//      val noteList: Array[(String, Map[String, String])] = xml.descendant.filter(_.label == "Notes").flatMap({ case notes =>
//        notes.child.map({ case note =>
//          val noteId = (note \ "@id").toString()
//          val map = note.child.filter(_.label == "Field").map(n => (n.attribute("name").getOrElse("").toString, n.text)).toMap
//          (noteId, map)
//        })
//      }).filter({case (noteId, map) =>
//        noteId.length > 0 && map.nonEmpty
//      }).toArray
//
//      val fileList: Array[(String, Map[String, String])] = xml.descendant.filter(_.label == "Files").flatMap({ case files =>
//        files.child.map({ case file =>
//          val fileId = (file \ "@id").toString()
//          val map = file.child.filter(_.label == "Field").map(n => (n.attribute("name").getOrElse("").toString, n.text)).toMap
//          (fileId, map)
//        })
//      }).filter({case (noteId, map) =>
//        noteId.length > 0 && map.nonEmpty
//      }).toArray

      Defect(defectId, fieldMap)

    })
  }

  def buildContagion(sc: SparkContext, defects: RDD[Defect]): (RDD[DefectApplication], RDD[ApplicationEdge]) = {

    val productsId = defects.flatMap(d => d.metadata.getOrElse("Product", Set(""))).filter(_.length > 0).distinct().zipWithIndex().mapValues(l => l + 1L).collectAsMap()
    val sProductsId = sc.broadcast(productsId)

    // Read metadata from our parsed XML
    val applicationDefects: RDD[DefectApplication] = defects.mapPartitions({case it: Iterator[Defect] =>

      val dic = sProductsId.value
      for(defect <- it) yield {

        val createdDate = defect.metadata.getOrElse("New-on", Set("")).head
        val closedDate = defect.metadata.getOrElse("Closed-on", Set("")).head
        val versions = defect.metadata.getOrElse("Version", Set("")) // Observed in
        val releases = defect.metadata.getOrElse("Integrated-releases", Set(""))
        val product = defect.metadata.getOrElse("Product", Set("")).head
        val productId = dic.getOrElse(product, 0L)
        val severity = defect.metadata.getOrElse("Severity", Set("")).filter(_.matches("\\d+")).map(_.toInt).min

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

        DefectApplication(
          defect.defectId,
          productId,
          product,
          createdTime,
          closedTime,
          severity,
          versions.toArray,
          releases.toArray
        )
      }

    }).filter(t => t.fromDate > 0L && t.toDate > 0L && t.appId > 0L)

    // Save this object back to HDFS
    applicationDefects.cache()

    // Build a Defect contagion vector
    val parentChildDefects = applicationDefects.flatMap({case node =>

      val fromDate = new DateTime(node.fromDate)
      val toDate = new DateTime(node.toDate)

      // Start from a day before the observation
      // TODO: Use optimized Window found earlier
      // 1 day window on a decade incident might sound a little bit too much
      var startDate = fromDate.minusDays(1)
      val sdf = new SimpleDateFormat("yyyy-MM-dd")
      var openDays = collection.mutable.MutableList[String]()

      while (startDate.isBefore(toDate.plusDays(1))){
        openDays += sdf.format(startDate.toDate)
        startDate = new DateTime(startDate.plusDays(1).toDate.getTime)
      }

      openDays.map({case day => (day, node)}).toList

    }).groupByKey().flatMap({case (day, it) =>

      // Sort list by date and only build parent to child vector
      val contagions = it.toList.sortBy(_.fromDate)
      for(i <- 0 to contagions.length - 2; j <- i + 1 to contagions.length - 1) yield {
        val a = contagions(i)
        val b = contagions(j)
        (a.defectId, b.defectId)
      }

    }).distinct().filter({case (parent, child) => parent != child})

    // Add to cache to compute only once
    parentChildDefects.cache()

    // Normalize to the total number of observations
    val observations = parentChildDefects.count().toDouble

    // Get one level up to Product contagion vector
    // Get the average time to propagate defect towards child
    val applicationJoin = applicationDefects.map(t => (t.defectId, t))
    val applicationContagions: RDD[ApplicationEdge] = parentChildDefects.join(applicationJoin).map({case (parent, (child, parentApp)) =>
      (child, parentApp)
    }).join(applicationJoin).map({case (child, (parentApp, childApp)) =>
      val elapsedDays = ((childApp.fromDate - parentApp.fromDate) / (1000 * 3600 * 24L)).toInt
      ((parentApp.appId, childApp.appId), (elapsedDays, 1))
    }).reduceByKey({case (v1, v2) =>
      (v1._1 + v2._1, v1._2 + v2._2)
    }).map({case ((parent, child), (elapsed, count)) =>
      val avgDays = elapsed / count
      ApplicationEdge(parent, child, count / observations, avgDays)
    })

    (applicationDefects, applicationContagions)

  }

  def computeRisk(sc: SparkContext, defects: RDD[DefectApplication], contagions: RDD[ApplicationEdge]) = {

    val edges = contagions.mapPartitions { case it: Iterator[ApplicationEdge] =>
      for(contagion <- it) yield {
        Edge(contagion.fromApp, contagion.toApp, contagion.weight)
      }
    }

    val graph = Graph.fromEdges(edges, 0L)
    val pageRanked = new ContagionPageRank().run(graph).vertices
    val maxContagion = pageRanked.map(_._2).max

    val contagionNodes = defects.map(a => ((a.appId, a.appName), a.severity)).groupByKey().mapValues({case it =>
      val severities = it.toList
      (severities.sum / severities.size.toDouble, severities.size)
    }).map({case ((appId, appName), (avgSeverity, totalDefects)) =>
      (appId, (appName, avgSeverity, totalDefects))
    }).join(pageRanked).map({case (appId, ((appName, avgSeverity, totalDefects), contagionRisk)) =>
      (appId, appName, avgSeverity, totalDefects, contagionRisk / maxContagion)
    })

    val contagionEdges = contagions.mapPartitions { case it: Iterator[ApplicationEdge] =>
      for(contagion <- it) yield {
        (contagion.fromApp, contagion.toApp, contagion.weight, contagion.averageDaysToPropagate)
      }
    }

    //TODO: Pregel

    (contagionNodes, contagionEdges)

  }

}
