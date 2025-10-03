package org.meshtastic.core.common.build

interface BuildConfigProvider {
  val isDebug: Boolean
  val applicationId: String
  val versionCode: Int
  val versionName: String
  val absoluteMinFwVersion: String
  val minFwVersion: String
}
