package org.hathitrust.htrc.tools.ef.metadata.bibframeenrich.models

import org.hathitrust.htrc.tools.ef.metadata.bibframeenrich.models.EntityTypes.Viaf


case class ProvisionActivityAgentEntity(label: String) extends Entity {
  override def toRawEntity: RawEntity = RawEntity(Viaf, label, None, None)
}
