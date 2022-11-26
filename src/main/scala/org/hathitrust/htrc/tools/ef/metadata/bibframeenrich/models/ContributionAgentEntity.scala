package org.hathitrust.htrc.tools.ef.metadata.bibframeenrich.models

import org.hathitrust.htrc.tools.ef.metadata.bibframeenrich.models.EntityTypes.Viaf
import org.hathitrust.htrc.tools.ef.metadata.bibframeenrich.models.LabelTypes._


case class ContributionAgentEntity(label: String, `type`: String) extends Entity {
  override def toRawEntity: RawEntity = {
    val queryType = `type` match {
      case "Person" | "Family" => personal
      case "Organization" | "Meeting" | "Jurisdiction" => corporate
      case _ => `type`
    }

    RawEntity(Viaf, label, None, Some(queryType))
  }
}
