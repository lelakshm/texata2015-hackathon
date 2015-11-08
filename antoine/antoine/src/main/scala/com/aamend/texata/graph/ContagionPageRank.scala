package com.aamend.texata.graph

import org.apache.spark.graphx._

import scala.reflect.ClassTag

class ContagionPageRank extends Serializable {

  def run[VD: ClassTag](
                        graph: Graph[Long, Double],
                        tol: Double = 0.001,
                        resetProb: Double = 0.15
                        ): Graph[Double, Double] = {

    // Set the vertex attributes to (initalPR, delta = 0)
    val pagerankGraph: Graph[(Double, Double), Double] = graph.mapVertices((id, attr) => (0.0, 0.0)).cache()

    // Define the three functions needed to implement PageRank in the GraphX version of Pregel
    def vertexProgram(id: VertexId, attr: (Double, Double), msgSum: Double): (Double, Double) = {
      val oldPR = attr._1
      val newPR = oldPR + (1.0 - resetProb) * msgSum
      (newPR, newPR - oldPR)
    }

    def sendMessage(edge: EdgeTriplet[(Double, Double), Double]) = {
      if (edge.srcAttr._2 > tol) {
        Iterator((edge.dstId, edge.srcAttr._2 * edge.attr))
      } else {
        Iterator.empty
      }
    }

    def messageCombiner(a: Double, b: Double): Double = a + b

    // The initial message received by all vertices in PageRank
    val initialMessage = resetProb / (1.0 - resetProb)

    // Execute a dynamic version of Pregel
    val vp = (id: VertexId, attr: (Double, Double), msgSum: Double) => vertexProgram(id, attr, msgSum)
    Pregel(pagerankGraph, initialMessage, activeDirection = EdgeDirection.Out)(
      vp, sendMessage, messageCombiner)
      .mapVertices((vid, attr) => attr._1)
  }

}
