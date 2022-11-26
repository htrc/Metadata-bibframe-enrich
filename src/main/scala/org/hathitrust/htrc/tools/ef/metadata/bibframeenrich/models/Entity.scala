package org.hathitrust.htrc.tools.ef.metadata.bibframeenrich.models

trait Entity {
  def label: String
  def toRawEntity: RawEntity
}


