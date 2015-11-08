package com.aamend.texata

import com.aamend.texata.model.Model.ContagionNodeAttribute
import org.apache.spark.graphx.{GraphLoader, Graph, Edge}
import org.apache.spark.{SparkConf, SparkContext}

import scala.util.Random

/**
 * Created by antoine on 08/11/2015.
 */
object DefectPropagation {

  def main(args: Array[String]): Unit = {

    val sc = new SparkContext(new SparkConf().setAppName(this.getClass.toString))

    val applicationNodeDir = "texata/contagion-nodes"
    val applicationEdgeDir = "texata/contagion-edges"

    val applicationsNodes = sc.textFile(applicationNodeDir).map({case str =>
      val a = str.split("\\t")
      val appId = a(0).toLong
      val appName = a(1)
      val avgSeverity = a(2)
      val totalDefect = a(3).toInt
      val risk = a(4).toDouble
      (appId, appName, avgSeverity, totalDefect, risk)
    })

    val applicationsEdges = sc.textFile(applicationEdgeDir).map({case str =>
      val a = str.split("\\t")
      val from = a(0).toLong
      val to = a(1).toLong
      val weight = a(2).toDouble
      val avgDays = a(3).toDouble
      (from, to, weight, avgDays)
    })

    //TODO: Find failed vertices
    val vertices = applicationsNodes.map({case (id, name, sev, defect, risk) =>
      // Initialize all nodes with time = 0
      (id, ContagionNodeAttribute(name, risk, Random.nextInt(100) == 0, 0))
    })

    val edges = applicationsEdges.map({case (from, to, weight, days) => Edge(from, to, days)})
    val graph: Graph[ContagionNodeAttribute, Double] = Graph.apply(vertices, edges)

    graph.cache()

    val deltaT = 10

    // Execute Pregel here for propagating the incident through

















  }


}
