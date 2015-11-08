package com.aamend.texata

import com.aamend.texata.model.Model.{ApplicationEdge, DefectApplication}
import org.apache.spark.graphx.{Edge, Graph}
import org.apache.spark.rdd.RDD
import org.apache.spark.{SparkConf, SparkContext}

object ComputeRisk {

  def main(args: Array[String]) : Unit = {

    val applicationDefectDir = "texata/defects-lights-objects"
    val applicationContagionDir = "texata/contagions"

    val sc = new SparkContext(new SparkConf().setAppName(this.getClass.toString))

    val contagions = sc.objectFile[ApplicationEdge](applicationContagionDir).cache()
    // Remove the 20% weakest links
    val list: RDD[Double] = contagions.map(_.weight).sortBy(d => d)
    val total = list.count().toDouble
    val min = list.take((0.5 * total).toInt).max

    val edges = contagions.filter(_.weight > min).mapPartitions { case it: Iterator[ApplicationEdge] =>
      for(contagion <- it) yield {
        Edge(contagion.fromApp, contagion.toApp, contagion.weight)
      }
    }

    /*
Edge(555,734,2.5573267411132893E-4)
Edge(983,404,8.524422470377632E-5)
Edge(133,232,2.5573267411132893E-4)
Edge(1140,1031,8.524422470377632E-5)
Edge(928,881,0.0011081749211490923)
Edge(514,388,0.005285141931634132)
Edge(447,762,1.7048844940755265E-4)
Edge(633,194,2.5573267411132893E-4)
Edge(265,87,2.5573267411132893E-4)
Edge(630,1099,8.524422470377632E-4)
     */

    val graph = Graph.fromEdges(edges, 0L)
    graph.cache()

    val pageRanked = graph.pageRank(0.001).vertices//new ContagionPageRank().run(graph).vertices
    val maxContagion = pageRanked.map(_._2).max
    val defects = sc.objectFile[DefectApplication](applicationDefectDir).cache()

    val nodesCsv = defects.map(a => ((a.appId, a.appName), a.severity)).groupByKey().mapValues({case it =>
      val severities = it.toList
      (severities.sum / severities.size.toDouble, severities.size)
    }).map({case ((appId, appName), (avgSeverity, totalDefects)) =>
      (appId, (appName, avgSeverity, totalDefects))
    }).join(pageRanked).map({case (appId, ((appName, avgSeverity, totalDefects), contagionRisk)) =>
      Array(appId, appName, avgSeverity, totalDefects, contagionRisk / maxContagion).mkString("\t")
    })

    /*
1104	de-info-infra	2.8225806451612905	62	0.044742126950741735
784	asr9k-pagent	5.333333333333333	3	0.01617808316487213
16	infra-rds	3.0	8	0.017618160699723502
320	qfp-intf-fr-relay	3.0	1	0.014680146568400955
80	compmgmt	3.75	12	0.023278391249737577
720	asr9k-testbench	3.980769230769231	52	0.14232587599607083
464	asr9k-qfp-pal-thor	2.0	5	0.03041070801893479
1008	asr9k-mr-aps	3.0	13	0.029956995275981436
1040	mpls-netio	3.0	14	0.05948081203715259
928	acl	3.2241379310344827	174	0.22318035843522357
     */

    val edgesCsv = contagions.mapPartitions { case it: Iterator[ApplicationEdge] =>
      for(contagion <- it) yield {
        Array(contagion.fromApp, contagion.toApp, contagion.weight, contagion.averageDaysToPropagate).mkString("\t")
      }
    }

    /*
    scala> edgesCsv.take(10).foreach(println)
555.0	734.0	2.5573267411132893E-4	2.8772955246913576
983.0	404.0	8.524422470377632E-5	0.18032407407407408
133.0	232.0	2.5573267411132893E-4	5.10346450617284
1140.0	1031.0	8.524422470377632E-5	4.458101851851852
928.0	881.0	0.0011081749211490923	4.924797008547009
514.0	388.0	0.005285141931634132	5.144517622461171
447.0	762.0	1.7048844940755265E-4	4.639895833333333
633.0	194.0	2.5573267411132893E-4	5.776523919753086
265.0	87.0	2.5573267411132893E-4	14.089745370370371
630.0	1099.0	8.524422470377632E-4	4.722927083333333
     */

    nodesCsv.saveAsTextFile("texata/contagion-nodes")
    edgesCsv.saveAsTextFile("texata/contagion-edges")

    val newIdMap = defects.map(a => ((a.appId, a.appName), a.severity)).groupByKey().mapValues({case it =>
      val severities = it.toList
      (severities.sum / severities.size.toDouble, severities.size)
    }).map({case ((appId, appName), (avgSeverity, totalDefects)) =>
      (appId, (appName, avgSeverity, totalDefects))
    }).join(pageRanked).flatMap({case (appId, ((appName, avgSeverity, totalDefects), contagionRisk)) =>
        for(i <- 0 to 1) yield {
          (appId, appName, i, avgSeverity.toInt, contagionRisk)
        }
    }).sortBy(_._1).zipWithIndex().map({case ((appId, appName, i, sev, risk), newId) =>
      ((appId, i), (newId, i, appName, sev, risk))
    }).collectAsMap()

    sc.parallelize(newIdMap.toSeq.map({case ((appId, member), (newId, tmp, appName, sev, risk)) =>
      s"$newId,$member,$appName,$sev,$risk"
    })).saveAsTextFile("/tmp/contagion-nodes")

    sc.parallelize(contagions.mapPartitions { case it: Iterator[ApplicationEdge] =>
      for (contagion <- it) yield {
        val key1 = (contagion.fromApp, 1)
        val key2 = (contagion.toApp, 0)
        (newIdMap.get(key1), newIdMap.get(key2), contagion.weight, contagion.averageDaysToPropagate)
      }
    }.filter(t => t._1.isDefined && t._2.isDefined && t._3 > 0.01).sortBy(_._3, ascending = false).take(1000)).map({case (v1,v2,w,d) => s"${v1.get._1},${v2.get._1},$d"}).saveAsTextFile("/tmp/contagion-edges")











  }

  def computePercentile(data: RDD[Double], tile: Double): Double = {
    val r = data.sortBy(x => x)
    val c = r.count()
    if (c == 1) r.first()
    else {
      val n = (tile / 100d) * (c + 1d)
      val k = math.floor(n).toLong
      val d = n - k
      if (k <= 0) r.first()
      else {
        val index = r.zipWithIndex().map(_.swap)
        val last = c
        if (k >= c) {
          index.lookup(last - 1).head
        } else {
          index.lookup(k - 1).head + d * (index.lookup(k).head - index.lookup(k - 1).head)
        }
      }
    }
  }

}
