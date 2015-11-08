package com.aamend.texata

import java.text.SimpleDateFormat
import java.util.Date

import com.aamend.texata.model.Model._
import com.typesafe.config.ConfigFactory
import org.apache.spark.{SparkConf, SparkContext}
import org.joda.time.DateTime

object BuildContagion {

  def main(args: Array[String]) = {

    val config = ConfigFactory.load()

    val inputDir = config.getString("texata.cdets.xml.output.dir")
    val applicationDefectDir = config.getString("texata.cdets.app.defect.dir")
    val applicationContagionDir = "texata/contagions"

    val sc = new SparkContext(new SparkConf().setAppName(this.getClass.toString))

    // Read defect and assign a unique ID for each application
    val defects = sc.objectFile[Defect](inputDir)
    val productsId = defects.flatMap(d => d.metadata.getOrElse("Product", Set(""))).filter(_.length > 0).distinct().zipWithIndex().mapValues(l => l + 1L).collectAsMap()
    val sProductsId = sc.broadcast(productsId)

    // Read metadata from our parsed XML
    val applicationDefects = defects.mapPartitions({case it: Iterator[Defect] =>

      val dic = sProductsId.value
      for(defect <- it) yield {

        val createdDate = defect.metadata.getOrElse("New-on", Set("")).head
        val closedDate = defect.metadata.getOrElse("Closed-on", Set("")).head
        val versions = defect.metadata.getOrElse("Version", Set("")) // Observed in
        val releases = defect.metadata.getOrElse("Integrated-releases", Set(""))
        val product = defect.metadata.getOrElse("Product", Set("")).head
        val productId = dic.getOrElse(product, 0L)
        val severities = defect.metadata.getOrElse("Severity", Set("")).filter(_.matches("\\d+")).map(_.toInt).toList.sorted
        val severity = if (severities.isEmpty) 1000 else severities.head

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
    applicationDefects.saveAsObjectFile(applicationDefectDir)

    // Build a Defect contagion vector
    val parentChildDefects = applicationDefects.filter({case node =>
      (node.toDate - node.fromDate) / (24 * 1000 * 3600.0) <= 15
    }).flatMap({case node =>

      val fromDate = new DateTime(node.fromDate)
      val toDate = new DateTime(node.toDate)

      // Start from a day before the observation
      var startDate = fromDate.minusDays(1)
      val sdf = new SimpleDateFormat("yyyy-MM-dd")
      var openDays = collection.mutable.MutableList[String]()

      while (startDate.isBefore(toDate.plusDays(1))){
        openDays += sdf.format(startDate.toDate)
        startDate = new DateTime(startDate.plusDays(1).toDate.getTime)
      }

      openDays.map({case day => (day, node)}).toList

    }).groupByKey().flatMap({case (pivot, it) =>
        for(node <- it) yield {
          (node, it)
        }
    }).flatMap({case (n1, it) =>
      for(n2 <- it) yield {
        (n1, n2)
      }
    }).filter({case (n1, n2) =>
      n1.fromDate <= n2.fromDate
    }).map({case (n1, n2) =>
      (n1.defectId, n2.defectId)
    }).distinct().filter({case (parent, child) =>
      parent != child
    })

    // Add to cache to compute only once
    parentChildDefects.cache()
    parentChildDefects.count()
    //res4: Long = 2826188 => total number of edges

    // Get one level up to Product contagion vector
    // Get the average time to propagate defect towards child
    val applicationJoin = applicationDefects.map(t => (t.defectId, t))
    val contagions = parentChildDefects.join(applicationJoin).map({case (parent, (child, parentApp)) =>
      (child, parentApp)
    }).join(applicationJoin).map({case (child, (parentApp, childApp)) =>
      val elapsedDays = (childApp.fromDate - parentApp.fromDate) / (1000 * 3600 * 24.0)
      ((parentApp.appId, childApp.appId), (elapsedDays, 1))
    }).reduceByKey({case (v1, v2) =>
      (v1._1 + v2._1, v1._2 + v2._2)
    }).map({case ((parent, child), (elapsed, count)) =>
      val avgDays = elapsed / count
      ApplicationEdge(parent, child, count, avgDays)
    })

    val maxCount = contagions.map(_.weight).max
    val normalizedContagions = contagions.map({case c =>
      ApplicationEdge(c.fromApp, c.toApp, c.weight / maxCount, c.averageDaysToPropagate)
    })

    /*
scala> normalizedContagions.take(10).foreach(println)
ApplicationEdge(555,734,2.5573267411132893E-4,2.8772955246913576)
ApplicationEdge(983,404,8.524422470377632E-5,0.18032407407407408)
ApplicationEdge(133,232,2.5573267411132893E-4,5.10346450617284)
ApplicationEdge(1140,1031,8.524422470377632E-5,4.458101851851852)
ApplicationEdge(928,881,0.0011081749211490923,4.924797008547009)
ApplicationEdge(514,388,0.005285141931634132,5.144517622461171)
ApplicationEdge(447,762,1.7048844940755265E-4,4.639895833333333)
ApplicationEdge(633,194,2.5573267411132893E-4,5.776523919753086)
ApplicationEdge(265,87,2.5573267411132893E-4,14.089745370370371)
ApplicationEdge(630,1099,8.524422470377632E-4,4.722927083333333)
     */


    // Save this object back to HDFS
    normalizedContagions.saveAsObjectFile(applicationContagionDir)

  }

}
