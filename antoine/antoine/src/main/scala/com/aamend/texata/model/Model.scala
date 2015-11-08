package com.aamend.texata.model

object Model {

  case class Defect(
                     defectId: String,
                     metadata: Map[String, Set[String]]
                   )

  case class DefectApplication(
                              defectId: String,
                              appId: Long,
                              appName: String,
                              fromDate: Long,
                              toDate: Long,
                              severity: Int,
                              impactedVersions: Array[String],
                              integratedReleases: Array[String]
                            )

  case class ApplicationEdge(
                            fromApp: Long,
                            toApp: Long,
                            weight: Double,
                            averageDaysToPropagate: Double
                            )

  case class ContagionNodeAttribute(
                                    name: String,
                                    risk: Double,
                                    isFailing: Boolean,
                                    time: Double
                                  )

}
