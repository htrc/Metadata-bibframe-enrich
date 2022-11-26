package org.hathitrust.htrc.tools.ef.metadata.bibframeenrich.models

import org.hathitrust.htrc.tools.ef.metadata.bibframeenrich.models.EntityTypes.Loc
import org.hathitrust.htrc.tools.ef.metadata.bibframeenrich.models.SubjectTypes._

object ComponentEntity {
  protected val componentQueryMap: Map[String, (String, String)] = Map(
    "GenreForm" -> (GenreForm, subjects),
    "Topic" -> (Topic, subjects),
    "Geographic" -> (Geographic, subjects)
  )
}
case class ComponentEntity(label: String, `type`: String) extends Entity {
  import ComponentEntity._

  override def toRawEntity: RawEntity = {
    val (rdfType, queryType) = componentQueryMap.getOrElse(`type`, `type` -> "")
    RawEntity(Loc, label, Some(rdfType), Option(queryType).filter(_.nonEmpty))
  }
}
