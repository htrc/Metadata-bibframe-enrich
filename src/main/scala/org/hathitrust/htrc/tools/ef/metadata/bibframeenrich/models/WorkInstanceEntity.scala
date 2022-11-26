package org.hathitrust.htrc.tools.ef.metadata.bibframeenrich.models

import org.hathitrust.htrc.tools.ef.metadata.bibframeenrich.models.EntityTypes.WorldCat

case class WorkInstanceEntity(label: String) extends Entity {
  override def toRawEntity: RawEntity = RawEntity(WorldCat, label, None, None)
}
