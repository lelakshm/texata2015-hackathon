package com.aamend.texata.graph

import com.aamend.texata.model.Model.ContagionNodeAttribute
import org.apache.spark.graphx._

/**
 * Created by antoine on 08/11/2015.
 */
class DefectPregelPropagation extends Serializable {

  val deltaTime = 10.0d
  val initialMessage = ContagionNodeAttribute("", 0, isFailing = false, 0)

  def propagate(graph: Graph[ContagionNodeAttribute, Double]) : Graph[ContagionNodeAttribute, Double] = {
    graph.cache()
    Pregel(graph, initialMessage, activeDirection = EdgeDirection.Out)(vertexProgram, sendMessage, mergeMsg)
  }

  private def vertexProgram(id: VertexId, attr: ContagionNodeAttribute, msg: ContagionNodeAttribute): ContagionNodeAttribute = {
    mergeMsg(attr, msg)
  }

  private def sendMessage(triplet: EdgeTriplet[ContagionNodeAttribute, Double]): Iterator[(VertexId, ContagionNodeAttribute)] = {

    // Send message if current Time + deltaTime < nextAvgTime
    // Send message only from unhealthy to healthy
    if(triplet.srcAttr.isFailing && !triplet.dstAttr.isFailing){
      // Might send a message
      if(triplet.srcAttr.time + deltaTime <= triplet.attr){
        val newAttr = ContagionNodeAttribute(triplet.srcAttr.name, triplet.srcAttr.risk, triplet.srcAttr.isFailing, triplet.srcAttr.time + triplet.attr)
        Iterator((triplet.dstId, newAttr))
      } else {
        Iterator.empty
      }
    } else {
      Iterator.empty
    }
  }

  private def mergeMsg(from: ContagionNodeAttribute, to: ContagionNodeAttribute): ContagionNodeAttribute = {

    ContagionNodeAttribute(to.name, to.risk, true, from.time)



    msg1.union(msg2)
  }

}
