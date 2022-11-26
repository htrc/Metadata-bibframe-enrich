package org.hathitrust.htrc.tools.ef.metadata.bibframeenrich.models

import org.hathitrust.htrc.tools.ef.metadata.bibframeenrich.models.EntityTypes.Loc
import org.hathitrust.htrc.tools.ef.metadata.bibframeenrich.models.SubjectTypes._

object SubjectEntity {
  protected val subjectQueryMap: Map[String, (String, String)] = Map(
    "http://id.loc.gov/ontologies/bibframe/Organization" -> (CorporateName, subjects),
    "http://id.loc.gov/ontologies/bibframe/Person" -> (PersonalName, names),
    "http://id.loc.gov/ontologies/bibframe/Meeting" -> (ConferenceName, names),
    "http://id.loc.gov/ontologies/bibframe/Jurisdiction" -> (CorporateName, names),
    "http://id.loc.gov/ontologies/bibframe/Family" -> (FamilyName, subjects),
    "http://www.loc.gov/mads/rdf/v1#Topic" -> (Topic, subjects),
    "http://www.loc.gov/mads/rdf/v1#Geographic" -> (Geographic, subjects),
    "http://www.loc.gov/mads/rdf/v1#Title" -> (Title, names),
    "http://www.loc.gov/mads/rdf/v1#Name" -> (PersonalName, names),
    "http://www.loc.gov/mads/rdf/v1#NameTitle" -> (NameTitle, names),
    "http://www.loc.gov/mads/rdf/v1#HierarchicalGeographic" -> (ComplexSubject, subjects),
    "http://www.loc.gov/mads/rdf/v1#CorporateName" -> (CorporateName, names),
    "http://www.loc.gov/mads/rdf/v1#ConferenceName" -> (ConferenceName, names),
    "http://www.loc.gov/mads/rdf/v1#ComplexSubject" -> (ComplexSubject, subjects)
  )
}

case class SubjectEntity(label: String, `type`: String) extends Entity {
  import SubjectEntity._

  override def toRawEntity: RawEntity = {
    val (rdfType, queryType) = subjectQueryMap.getOrElse(`type`, "" -> `type`)
    RawEntity(Loc, label, Option(rdfType).filter(_.nonEmpty), Some(queryType))
  }
}
