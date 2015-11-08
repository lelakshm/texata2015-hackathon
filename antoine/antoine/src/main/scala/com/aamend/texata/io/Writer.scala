package com.aamend.texata.io

import java.io.PrintStream
import java.util.Scanner

import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.{FSDataOutputStream, FileSystem, Path}

object Writer {

  def writeLocal(file: String, array: Array[String]) = {
    printToFile(new java.io.File(file)) { p =>
      array.foreach(p.println)
    }
  }

  def readHdfs(input: String): List[String] = {

    val conf = new Configuration()
    val hdfsCoreSitePath = new Path("/etc/hadoop/core-site.xml")
    val hdfsHDFSSitePath = new Path("/etc/hadoop/hdfs-site.xml")

    conf.addResource(hdfsCoreSitePath)
    conf.addResource(hdfsHDFSSitePath)
    conf.set("fs.hdfs.impl", "org.apache.hadoop.hdfs.DistributedFileSystem")
    conf.set("fs.file.impl", "org.apache.hadoop.fs.LocalFileSystem")

    val fileSystem = FileSystem.get(conf)
    val path = new Path(input)
    val is = fileSystem.open(path)

    val scanner = new Scanner(is)
    val list = collection.mutable.MutableList[String]()
    while (scanner.hasNextLine) {
      list += scanner.nextLine()
    }

    scanner.close()
    list.toList
  }

  def writeHdfs(output: String, data: List[String]) = {

    val conf = new Configuration()
    val hdfsCoreSitePath = new Path("/etc/hadoop/core-site.xml")
    val hdfsHDFSSitePath = new Path("/etc/hadoop/hdfs-site.xml")

    conf.addResource(hdfsCoreSitePath)
    conf.addResource(hdfsHDFSSitePath)
    conf.set("fs.hdfs.impl", "org.apache.hadoop.hdfs.DistributedFileSystem")
    conf.set("fs.file.impl", "org.apache.hadoop.fs.LocalFileSystem")

    val fileSystem = FileSystem.get(conf)
    val path = new Path(output)
    val os: FSDataOutputStream = fileSystem.create(path)

    val printStream = new PrintStream(os)
    data.foreach(d => printStream.print(d + "\n"))
    printStream.close()

  }

  private def printToFile(f: java.io.File)(op: java.io.PrintWriter => Unit) {
    val p = new java.io.PrintWriter(f)
    try { op(p) } finally { p.close() }
  }

}
