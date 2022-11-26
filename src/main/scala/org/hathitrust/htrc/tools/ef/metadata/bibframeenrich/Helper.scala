package org.hathitrust.htrc.tools.ef.metadata.bibframeenrich

import java.net.URI
import java.util.UUID

import org.hathitrust.htrc.tools.ef.metadata.bibframeenrich.models._
import org.hathitrust.htrc.tools.ef.metadata.bibframeenrich.utils.encoding.UriEncoding
import org.hathitrust.htrc.tools.scala.implicits.StringsImplicits._
import org.slf4j.{Logger, LoggerFactory}

import scala.io.{Codec, StdIn}
import scala.xml._

object Helper {
  @transient lazy val logger: Logger = LoggerFactory.getLogger(Main.appName)
  private val rdfNs = "http://www.w3.org/1999/02/22-rdf-syntax-ns#"
  private val utf8Charset = Codec.UTF8.charSet


  def readStdIn(): Seq[String] = Iterator.continually(StdIn.readLine()).takeWhile(_ != null).toList

  def generateInternalId(entity: Entity): String = {
    val encodedLabel = UriEncoding.encodePathSegment(entity.label, utf8Charset)
    val entityUri = entity match {
      case WorkInstanceEntity(_) =>
        new URI(s"http://catalog.library.illinois.edu/lod/entities/Works/ht/$encodedLabel")

      case ContributionAgentEntity(_, agentType) =>
        val typeSegment = UriEncoding.encodePathSegment(agentType, utf8Charset)
        new URI(s"http://catalogdata.library.illinois.edu/lod/entities/ContributionAgent/$typeSegment/ht/$encodedLabel")

      case ProvisionActivityAgentEntity(_) =>
        new URI(s"http://catalogdata.library.illinois.edu/lod/entities/ProvisionActivityAgent/ht/$encodedLabel")

      case SubjectEntity(_, subjectType) =>
        val typeSegment = UriEncoding.encodePathSegment(subjectType, utf8Charset)
        new URI(s"http://catalogdata.library.illinois.edu/lod/entities/Subjects/$typeSegment/ht/$encodedLabel")

      case ComponentEntity(_, componentType) =>
        val typeSegment = UriEncoding.encodePathSegment(componentType, utf8Charset)
        new URI(s"http://catalogdata.library.illinois.edu/lod/entities/SubjectComponents/$typeSegment/ht/$encodedLabel")

      case _ => throw new IllegalArgumentException(entity.toString)
    }

    entityUri.toString
  }

  def generateBlankNodeId: String = s"urn:uuid:${UUID.randomUUID().toString}"

  def transform(node: Node, pfs: Seq[PartialFunction[Node, Node]]): Node =
    pfs.find(_.isDefinedAt(node)).getOrElse(identity[Node](_)).apply(node) match {
      case e: Elem => e.copy(child = e.child map (c => transform(c, pfs)))
      case other => other
    }

  def enrichWork(xml: Elem, entitiesIdMap: Map[RawEntity, Option[String]]): Seq[PartialFunction[Node, Node]] = {
    val work = (xml \ "Work").head
    val instance = (xml \ "Instance").head
    val instanceId = instance \@ s"{$rdfNs}about"
    val instanceOf = (instance \ "instanceOf").head

    val workInstanceEntity = WorkInstanceEntity(instanceId)
    val newWorkId = entitiesIdMap.get(workInstanceEntity.toRawEntity).flatten match {
      case Some(v) => v
      case None => generateInternalId(workInstanceEntity)
    }

    val transformRule:PartialFunction[Node, Node] = {
      case `work` => work.asInstanceOf[Elem] % Attribute("rdf", "about", Text(newWorkId), Null)
      case `instanceOf` => instanceOf.asInstanceOf[Elem] % Attribute("rdf", "resource", Text(newWorkId), Null)
    }

    List(transformRule)
  }

  def enrichContributionAgents(work: Node , entitiesIdMap: Map[RawEntity, Option[String]]): Seq[PartialFunction[Node, Node]] = {
    (work \ "contribution" \ "Contribution" \ "agent" \ "Agent")
      .withFilter(agent => (agent \ "type").exists(_.attribute(rdfNs, "resource").isDefined))
      .map { agent =>
        val label = (agent \ "label").text
        val agentEntities = (agent \ "type")
          .iterator
          .collect {
            case node if node.attribute(rdfNs, "resource").isDefined => node \@ s"{$rdfNs}resource"
          }
          .map(_.takeRightWhile(_ != '/'))
          .map(ContributionAgentEntity(label, _))
          .toList

        val agentId = agentEntities
          .map(_.toRawEntity)
          .toSet[RawEntity]
          .iterator
          .map(entitiesIdMap.get(_).flatten.map("http://www.viaf.org/viaf/" +))
          .collectFirst {
            case Some(id) => id
          }
          .getOrElse(generateInternalId(agentEntities.head))

        val transformRule: PartialFunction[Node, Node] = {
          case `agent` => agent.asInstanceOf[Elem] % Attribute("rdf", "about", Text(agentId), Null)
        }

        transformRule
      }
  }

  def enrichProvisionActivityAgents(instance: Node, entitiesIdMap: Map[RawEntity, Option[String]]): Seq[PartialFunction[Node, Node]] = {
    (instance \ "provisionActivity" \ "ProvisionActivity" \ "agent" \ "Agent")
      .map { agent =>
        val label = (agent \ "label").text
        val agentEntity = ProvisionActivityAgentEntity(label)
        val agentId = entitiesIdMap.get(agentEntity.toRawEntity).flatten match {
          case Some(v) => v
          case None => generateInternalId(agentEntity)
        }

        val transformRule: PartialFunction[Node, Node] = {
          case `agent` => agent.asInstanceOf[Elem] % Attribute("rdf", "about", Text(agentId), Null)
        }

        transformRule
      }
  }

  def enrichSubjects(work: Node, entitiesIdMap: Map[RawEntity, Option[String]]): Seq[PartialFunction[Node, Node]] = {
    (work \ "subject" \ "_")
      .withFilter(_.label != "Temporal")
      .flatMap { subject =>
        val label = (subject \ "label").text
        val subjectEntities = (subject \ "type")
          .iterator
          .collect {
            case node if node.attribute(rdfNs, "resource").isDefined => node \@ s"{$rdfNs}resource"
          }
          .map(_.takeRightWhile(_ != '/'))
          .map(SubjectEntity(label, _))
          .toList

        if (subjectEntities.nonEmpty) {
          val subjectId = subjectEntities
            .map(_.toRawEntity)
            .toSet[RawEntity]
            .iterator
            .map(entitiesIdMap.get(_).flatten)
            .collectFirst {
              case Some(id) => id
            }
            .getOrElse(generateInternalId(subjectEntities.head))

          val transformRule: PartialFunction[Node, Node] = {
            case `subject` => subject.asInstanceOf[Elem] % Attribute("rdf", "about", Text(subjectId), Null)
          }

          transformRule +: enrichSubjectComponents(subject, entitiesIdMap)
        } else {
          enrichSubjectComponents(subject, entitiesIdMap)
        }
      }
  }

  def enrichSubjectComponents(subject: Node, entitiesIdMap: Map[RawEntity, Option[String]]): Seq[PartialFunction[Node, Node]] = {
    (subject \ "componentList" \ "_")
      .withFilter(_.label != "Temporal")
      .map { component =>
        val componentType = component.label
        val label = (component \ "authoritativeLabel").text
        val componentEntity = ComponentEntity(label, componentType)
        val componentId = entitiesIdMap.get(componentEntity.toRawEntity).flatten match {
          case Some(v) => v
          case None => generateInternalId(componentEntity)
        }

        val transformRule: PartialFunction[Node, Node] = {
          case `component` => component.asInstanceOf[Elem] % Attribute("rdf", "about", Text(componentId), Null)
        }

        transformRule
      }
  }

  def enrichVolume(xml: Elem, entitiesIdMap: Map[RawEntity, Option[String]]): Elem = {
    val work = (xml \ "Work").head
    val workId = work \@ s"{$rdfNs}about"

    val instance = (xml \ "Instance").head
    val instanceId = instance \@ s"{$rdfNs}about"

    val item = (xml \ "Item").head
    val itemId = item \@ s"{$rdfNs}about"

    // sanity checks
    assume((instance \ "instanceOf").exists(_ \@ s"{$rdfNs}resource" equals workId), "Work.id and Instance.instanceOf mismatch!")
    assume((work \ "hasInstance").exists(_ \@ s"{$rdfNs}resource" equals instanceId), "Instance.id and Work.hasInstance mismatch!")
    assume((instance \ "hasItem").exists(_ \@ s"{$rdfNs}resource" equals itemId), "Item.id and Instance.hasItem mismatch!")
    assume((item \ "itemOf").exists(_ \@ s"{$rdfNs}resource" equals instanceId), "Instance.id and Item.itemOf mismatch!")

    val exampleOrgTransform: PartialFunction[Node, Node] = {
      case e: Elem if e.attribute(rdfNs, "about").exists(_.text.startsWith("http://example.org/")) =>
        e % Attribute("rdf", "about", Text(generateBlankNodeId), Null)

      case e: Elem if e.attribute(rdfNs, "resource").exists(_.text.startsWith("http://example.org/")) =>
        e % Attribute("rdf", "resource", Text(generateBlankNodeId), Null)
    }

    val transforms =
      enrichWork(xml, entitiesIdMap) ++
      enrichContributionAgents(work, entitiesIdMap) ++
      enrichProvisionActivityAgents(instance, entitiesIdMap) ++
      enrichSubjects(work, entitiesIdMap) :+
      exampleOrgTransform  // must be last

    transform(xml, transforms).asInstanceOf[Elem]
  }
}