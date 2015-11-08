package com.aamend.texata

import com.aamend.texata.model.Model._
import org.apache.spark.sql.SQLContext
import org.apache.spark.{SparkConf, SparkContext}

object EDA {

  //TODO: Run it from Zeppelin and get SQL results
  case class Point(days: Double, defects: Long)

  def main(args: Array[String]) = {

    val applicationDefectDir = "texata/defects-lights-objects"

    val sc = new SparkContext(new SparkConf().setAppName(this.getClass.toString))
    val sqlContext = new SQLContext(sc)
    import sqlContext.implicits._

    val defects = sc.objectFile[DefectApplication](applicationDefectDir).cache()

    val fixedDates = defects.filter(_.severity == 2).map(d => (d.toDate - d.fromDate) / (1000 * 3600 * 24.0))
    val (times, n) = fixedDates.histogram(50)

    val points = sc.parallelize(times.zip(n).toSeq).map({case (x, y) => Point(x, y)})

    // Eye ball average time for grouping defects while maximizing resources
    points.toDF().registerTempTable("cdets")

    // TODO: Time resolution for Sev1, Sev2, Sev3 incidents
    sqlContext.sql("SELECT days, defects FROM cdets ORDER BY days ASC")
    //Sev1: Within a day: 1500+ resolved immediatly
    //Sev2: Between 1 to 30 days
    //Sev3: Between 0 to 40 days

    // TODO: How many Sev1, Sev2, Sev3 incidents
    //Array((1,2278), (2,18968), (3,29935), (4,5411), (5,1023), (6,13683), (7,27))

    defects.toDF().registerTempTable("defects")

    // TODO: How many projects and number of Sev1, Sev2, Sev3 incidents
    sqlContext.sql("SELECT severity, product, count(1) FROM defect GROUP BY severity, product")

/*
  Top failures are all in asr9k application / components / TODO: Would need a better look at description

asr9k-triage	2,695
asr9k-prm	2,110
asr9k-qos	1,489
asr9k-l2vpn	1,231
asr9k-lc-typhoon	1,163
asr9k-lc-np	1,138
asr9k-diags	1,067
 */

    // TODO: How many versions per components

    defects.flatMap(d => d.impactedVersions.toSet.map(version => (d.appName, 1))).reduceByKey(_+_).sortBy(_._2, ascending = false)

    // Quite a lot of version in asr9k components
/*
(asr9k-diags,3030)
(asr9k-prm,3024)
(asr9k-triage,2858)
(asr9k-qos,1970)
(asr9k-l2vpn,1867)
(asr9k-lc-np,1782)
(asr9k-lc-typhoon,1773)
(l2vpn,1318)
(asr9k-ether-ctrl,1316)
(asr9k-lc-fpgas,1148)
(iedge4710,1078)
(asr9k-ipmcast,940)
(asr9k-fib-common,906)
(asr9k-fia,847)
(asr9k-sc-shelfmgr,827)
(asr9k-sc-envmon,812)
(asr9k-avsm-infra,692)
(asr9k-punt,654)
(asr9k-sc-invmgr,652)
(asr9k-fib,618)
(asr9k-rsp-fpgas,551)
(asr9k-pm,521)
(asr9k-sw-forge,512)
(asr9k-lpts,475)
(asr9k-acl,443)
(fib-common,439)
(asr9k-rommon,437)
(asr9k-sip700-infra,430)
(ipv4-dhcpd,429)
(subscriber-ipsub,417)
(asr9k-cluster,412)
(asr9k-lc-cpp,395)
(sdac-satellite,393)
(asr9k-fpd,392)
(asr9k-bng,388)
 */

  }

}
