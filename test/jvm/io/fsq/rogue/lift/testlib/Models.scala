// Copyright 2011 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.rogue.lift.testlib

import com.mongodb.{DB, DBCollection}
import io.fsq.rogue.{LatLong, LegacyMongo, ShardKey, Sharded}
import io.fsq.rogue.connection.{BlockingMongoClientManager, DefaultMongoIdentifier}
import io.fsq.rogue.index.{Asc, Desc, IndexModifier, IndexedRecord, TwoD}
import io.fsq.rogue.lift.{HasMongoForeignObjectId, ObjectIdKey}
import io.fsq.rogue.lift.LiftRogue._
import net.liftweb.mongodb.record._
import net.liftweb.mongodb.record.field._
import net.liftweb.record.field._
import net.liftweb.util.ConnectionIdentifier
import org.bson.types.ObjectId

/////////////////////////////////////////////////
// Sample records for testing
/////////////////////////////////////////////////

object RogueTestMongoIdentifier extends DefaultMongoIdentifier("rogue_mongo") with ConnectionIdentifier {
  override def jndiName = name
}

object RogueLiftTestMetaRecord {
  var clientManager: BlockingMongoClientManager = null
}

trait RogueLiftTestMetaRecord[Record <: MongoRecord[Record]] extends MongoMetaRecord[Record] {
  self: Record =>

  override def useColl[T](f: DBCollection => T): T = {
    useDb(db => f(db.getCollection(collectionName)))
  }

  override def useDb[T](f: DB => T): T = {
    val (client, dbName) = RogueLiftTestMetaRecord.clientManager.getClientOrThrow(
      RogueTestMongoIdentifier
    )
    f(LegacyMongo.getDB(client, dbName))
  }
}

object VenueStatus extends Enumeration {
  val open = Value("Open")
  val closed = Value("Closed")
}

class Venue extends MongoRecord[Venue] with ObjectIdKey[Venue] with IndexedRecord[Venue] {
  def meta = Venue
  object legacyid extends LongField(this) { override def name = "legid" }
  object userid extends LongField(this)
  object venuename extends StringField(this, 255)
  object mayor extends LongField(this)
  object mayor_count extends LongField(this)
  object closed extends BooleanField(this)
  object tags extends MongoListField[Venue, String](this)
  object popularity extends MongoListField[Venue, Long](this)
  object categories extends MongoListField[Venue, ObjectId](this)
  object geolatlng extends MongoCaseClassField[Venue, LatLong](this) { override def name = "latlng" }
  object last_updated extends DateField(this)
  object status extends EnumNameField(this, VenueStatus) { override def name = "status" }
  object claims extends BsonRecordListField(this, VenueClaimBson)
  object lastClaim extends BsonRecordField(this, VenueClaimBson)
}
object Venue extends Venue with RogueLiftTestMetaRecord[Venue] {
  override def collectionName = "venues"
  override def connectionIdentifier = RogueTestMongoIdentifier

  object CustomIndex extends IndexModifier("custom")
  val idIdx = Venue.index(_._id, Asc)
  val mayorIdIdx = Venue.index(_.mayor, Asc, _._id, Asc)
  val mayorIdClosedIdx = Venue.index(_.mayor, Asc, _._id, Asc, _.closed, Asc)
  val legIdx = Venue.index(_.legacyid, Desc)
  val geoIdx = Venue.index(_.geolatlng, TwoD)
  val geoCustomIdx = Venue.index(_.geolatlng, CustomIndex, _.tags, Asc)
  override val mongoIndexList = Vector(idIdx, mayorIdIdx, mayorIdClosedIdx, legIdx, geoIdx, geoCustomIdx)

  trait FK[T <: FK[T]] extends MongoRecord[T] {
    self: T =>
    object venueid extends ObjectIdField[T](this) with HasMongoForeignObjectId[Venue] {
      override def name = "vid"
    }
  }
}

object ClaimStatus extends Enumeration {
  val pending = Value("Pending approval")
  val approved = Value("Approved")
}

object RejectReason extends Enumeration {
  val tooManyClaims = Value("too many claims")
  val cheater = Value("cheater")
  val wrongCode = Value("wrong code")
}

class VenueClaim extends MongoRecord[VenueClaim] with ObjectIdKey[VenueClaim] with Venue.FK[VenueClaim] {
  def meta = VenueClaim
  object userid extends LongField(this) { override def name = "uid" }
  object status extends EnumNameField(this, ClaimStatus)
  object reason extends EnumField(this, RejectReason)
  object date extends DateField(this)
}
object VenueClaim extends VenueClaim with RogueLiftTestMetaRecord[VenueClaim] {
  override def fieldOrder = List(status, _id, userid, venueid, reason)
  override def collectionName = "venueclaims"
  override def connectionIdentifier = RogueTestMongoIdentifier
}

class VenueClaimBson extends BsonRecord[VenueClaimBson] {
  def meta = VenueClaimBson
  object userid extends LongField(this) { override def name = "uid" }
  object status extends EnumNameField(this, ClaimStatus)
  object source extends BsonRecordField(this, SourceBson)
  object date extends DateField(this)
}
object VenueClaimBson extends VenueClaimBson with BsonMetaRecord[VenueClaimBson] {
  override def fieldOrder = List(status, userid, source, date)
}

class SourceBson extends BsonRecord[SourceBson] {
  def meta = SourceBson
  object name extends StringField(this, 100)
  object url extends StringField(this, 200)
}
object SourceBson extends SourceBson with BsonMetaRecord[SourceBson] {
  override def fieldOrder = List(name, url)
}

case class OneComment(timestamp: String, userid: Long, comment: String)
class Comment extends MongoRecord[Comment] with ObjectIdKey[Comment] {
  def meta = Comment
  object comments extends MongoCaseClassListField[Comment, OneComment](this)
}
object Comment extends Comment with RogueLiftTestMetaRecord[Comment] {
  override def collectionName = "comments"
  override def connectionIdentifier = RogueTestMongoIdentifier

  val idx1 = Comment.index(_._id, Asc)
}

class Tip extends MongoRecord[Tip] with ObjectIdKey[Tip] {
  def meta = Tip
  object legacyid extends LongField(this) { override def name = "legid" }
  object counts extends MongoMapField[Tip, Long](this)
  object userid extends LongField(this)
}
object Tip extends Tip with RogueLiftTestMetaRecord[Tip] {
  override def collectionName = "tips"
  override def connectionIdentifier = RogueTestMongoIdentifier
}

class Like extends MongoRecord[Like] with ObjectIdKey[Like] with Sharded {
  def meta = Like
  object userid extends LongField(this) with ShardKey[Long]
  object checkin extends LongField(this)
  object tip extends ObjectIdField(this)
}
object Like extends Like with RogueLiftTestMetaRecord[Like] {
  override def collectionName = "likes"
  override def connectionIdentifier = RogueTestMongoIdentifier
}

object ConsumerPrivilege extends Enumeration {
  val awardBadges = Value("Award badges")
}

class OAuthConsumer extends MongoRecord[OAuthConsumer] with ObjectIdKey[OAuthConsumer] {
  def meta = OAuthConsumer
  object privileges extends MongoListField[OAuthConsumer, ConsumerPrivilege.Value](this)
}
object OAuthConsumer extends OAuthConsumer with RogueLiftTestMetaRecord[OAuthConsumer] {
  override def collectionName = "oauthconsumers"
  override def connectionIdentifier = RogueTestMongoIdentifier
}

// Used for selectCase tests.
case class V1(legacyid: Long)
case class V2(legacyid: Long, userid: Long)
case class V3(legacyid: Long, userid: Long, mayor: Long)
case class V4(legacyid: Long, userid: Long, mayor: Long, mayor_count: Long)
case class V5(legacyid: Long, userid: Long, mayor: Long, mayor_count: Long, closed: Boolean)
case class V6(legacyid: Long, userid: Long, mayor: Long, mayor_count: Long, closed: Boolean, tags: List[String])

class CalendarFld private () extends MongoRecord[CalendarFld] with ObjectIdPk[CalendarFld] {
  def meta = CalendarFld

  object inner extends BsonRecordField(this, CalendarInner)
}

object CalendarFld extends CalendarFld with RogueLiftTestMetaRecord[CalendarFld] {
  override def connectionIdentifier = RogueTestMongoIdentifier
}

class CalendarInner private () extends BsonRecord[CalendarInner] {
  def meta = CalendarInner

  object date extends DateTimeField(this) //actually calendar field, not joda DateTime
}

object CalendarInner extends CalendarInner with BsonMetaRecord[CalendarInner]
