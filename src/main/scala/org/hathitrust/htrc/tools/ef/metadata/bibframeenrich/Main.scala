package org.hathitrust.htrc.tools.ef.metadata.bibframeenrich

import java.io.{File, PrintWriter}
import java.nio.file.Files

import com.gilt.gfc.time.Timer
import org.apache.hadoop.fs.Path
import org.apache.spark.sql.SparkSession
import org.apache.spark.{SparkConf, SparkContext}
import org.hathitrust.htrc.tools.ef.metadata.bibframeenrich.Helper.logger
import org.hathitrust.htrc.tools.ef.metadata.bibframeenrich.models.EntityResult
import org.hathitrust.htrc.tools.scala.io.IOUtils.using
import org.hathitrust.htrc.tools.spark.errorhandling.ErrorAccumulator
import org.hathitrust.htrc.tools.spark.errorhandling.RddExtensions._
import play.api.libs.json.Json

import scala.io.{Codec, Source}
import scala.language.reflectiveCalls
import scala.util.{Failure, Success, Try}
import scala.xml.{Elem, XML}

object Main {
  val appName: String = "bibframe-enrich"

  def main(args: Array[String]): Unit = {
    val conf = new Conf(args.toIndexedSeq)
    val inputPath = conf.inputPath().toString
    val outputPath = conf.outputPath().toString
    val entitiesFile = conf.entitiesFile()

    conf.outputPath().mkdirs()

    // set up logging destination
    conf.sparkLog.toOption match {
      case Some(logFile) => System.setProperty("spark.logFile", logFile)
      case None =>
    }
    System.setProperty("logLevel", conf.logLevel().toUpperCase)

    // set up Spark context
    val sparkConf = new SparkConf()
    sparkConf.setAppName(appName)
    sparkConf.setIfMissing("spark.master", "local[*]")

    val spark = SparkSession.builder()
      .config(sparkConf)
      .getOrCreate()

//    import spark.implicits._

    implicit val sc: SparkContext = spark.sparkContext
    implicit val codec: Codec = Codec.UTF8

    val numPartitions = conf.numPartitions.getOrElse(sc.defaultMinPartitions)

    logger.info("Starting...")

    // record start time
    val t0 = System.nanoTime()

    logger.info(s"Loading entities from $entitiesFile")
    val entitiesBcast = {
      val entitiesMap = using(Source.fromFile(entitiesFile)) { entitiesSource =>
        val entityResults = entitiesSource.getLines().map(line => Json.parse(line).as[EntityResult])
        entityResults.map(er => er.entity -> er.value).toMap
      }
      logger.info(f"Loaded ${entitiesMap.size}%,d entities")
      sc.broadcast(entitiesMap)
    }

    val xmlParseErrorAccumulator = new ErrorAccumulator[(String, String), String](_._1)
    val bibframeXmlRDD = sc
      .sequenceFile[String, String](inputPath, minPartitions = numPartitions)
      .tryMapValues(XML.loadString)(xmlParseErrorAccumulator)

//    val xmlEnrichErrorAccumulator = new ErrorAccumulator[(String, Elem), String](_._1)
//    val enrichedBibframeXmlRDD = bibframeXmlRDD
//      .tryMapValues(Helper.enrichVolume(_, entitiesBcast.value).toString())(xmlEnrichErrorAccumulator)

    val enrichedBibframeXmlRDD = bibframeXmlRDD
        .flatMap { case (k, e) => Try(Helper.enrichVolume(e, entitiesBcast.value)) match {
          case Success(result) => Some(k -> result.toString())
          case Failure(t) =>
            logger.error(s"enrichVolume: id=$k", t)
            None
        }}

//    enrichedBibframeXmlRDD.foreach { case (id, xml) =>
//      val fname = id.replaceAllLiterally(":", "+").replaceAllLiterally("/", "=")
//      using(new PrintWriter(new File(outputPath, fname + ".xml"), "UTF-8"))(_.print(xml))
//    }
    enrichedBibframeXmlRDD.saveAsSequenceFile(outputPath + "/enriched")

//    if (xmlParseErrorAccumulator.nonEmpty || xmlEnrichErrorAccumulator.nonEmpty)
    if (xmlParseErrorAccumulator.nonEmpty)
      logger.info("Writing error report(s)...")

    if (xmlParseErrorAccumulator.nonEmpty)
      xmlParseErrorAccumulator.saveErrors(new Path(outputPath, "xmlparse_errors.txt"))

//    if (xmlEnrichErrorAccumulator.nonEmpty)
//      xmlEnrichErrorAccumulator.saveErrors(new Path(outputPath, "enrich_errors.txt"))

    // record elapsed time and report it
    val t1 = System.nanoTime()
    val elapsed = t1 - t0

    logger.info(f"All done in ${Timer.pretty(elapsed)}")
  }

}
