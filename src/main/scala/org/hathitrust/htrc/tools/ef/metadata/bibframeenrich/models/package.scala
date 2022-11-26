package org.hathitrust.htrc.tools.ef.metadata.bibframeenrich

import play.api.libs.json.{Json, Reads}

package object models {

  implicit val entityResultReads: Reads[EntityResult] = Json.reads[EntityResult]

}
