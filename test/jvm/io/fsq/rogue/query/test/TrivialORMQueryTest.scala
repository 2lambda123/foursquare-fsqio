// Copyright 2017 Foursquare Labs Inc. All Rights Reserved.

package io.fsq.rogue.query.test

import com.twitter.util.{Await, Future}
import io.fsq.common.concurrent.Futures
import io.fsq.field.{OptionalField, RequiredField}
import io.fsq.rogue.{InitialState, Query, QueryOptimizer, Rogue}
import io.fsq.rogue.MongoHelpers.AndCondition
import io.fsq.rogue.adapter.{AsyncMongoClientAdapter, BlockingMongoClientAdapter, BlockingResult}
import io.fsq.rogue.adapter.callback.twitter.TwitterFutureMongoCallbackFactory
import io.fsq.rogue.connection.MongoIdentifier
import io.fsq.rogue.connection.testlib.RogueMongoTest
import io.fsq.rogue.query.QueryExecutor
import io.fsq.rogue.query.testlib.{TrivialORMMetaRecord, TrivialORMMongoCollectionFactory, TrivialORMRecord,
    TrivialORMRogueSerializer}
import org.bson.Document
import org.bson.types.ObjectId
import org.junit.{Before, Test}
import org.specs2.matcher.JUnitMustMatchers
import scala.collection.JavaConverters.{asScalaBufferConverter, bufferAsJavaListConverter, mapAsJavaMapConverter,
    mapAsScalaMapConverter}


case class SimpleRecord(
  id: ObjectId = new ObjectId,
  boolean: Option[Boolean] = None,
  int: Option[Int] = None,
  long: Option[Long] = None,
  double: Option[Double] = None,
  string: Option[String] = None,
  array: Option[Array[Int]] = None,
  map: Option[Map[String, Int]] = None
) extends TrivialORMRecord {
  override type Self = SimpleRecord
  override def meta = SimpleRecord
}

object SimpleRecord extends TrivialORMMetaRecord[SimpleRecord] {

  val id = new RequiredField[ObjectId, SimpleRecord.type] {
    override val owner = SimpleRecord
    override val name = "_id"
    override def defaultValue: ObjectId = new ObjectId
  }

  val boolean = new OptionalField[Boolean, SimpleRecord.type] {
    override val owner = SimpleRecord
    override val name = "boolean"
  }

  val int = new OptionalField[Int, SimpleRecord.type] {
    override val owner = SimpleRecord
    override val name = "int"
  }

  val long = new OptionalField[Long, SimpleRecord.type] {
    override val owner = SimpleRecord
    override val name = "long"
  }

  val double = new OptionalField[Double, SimpleRecord.type] {
    override val owner = SimpleRecord
    override val name = "double"
  }

  val string = new OptionalField[String, SimpleRecord.type] {
    override val owner = SimpleRecord
    override val name = "string"
  }

  val array = new OptionalField[Array[Int], SimpleRecord.type] {
    override val owner = SimpleRecord
    override val name = "array"
  }

  val map = new OptionalField[Map[String, Int], SimpleRecord.type] {
    override val owner = SimpleRecord
    override val name = "map"
  }

  override val collectionName = "test_records"

  override val mongoIdentifier = MongoIdentifier("test")

  override def fromDocument(document: Document): SimpleRecord = {
    SimpleRecord(
      document.getObjectId(id.name),
      Option(document.getBoolean(boolean.name)),
      Option(document.getInteger(int.name)),
      Option(document.getLong(long.name)),
      Option(document.getDouble(double.name)),
      Option(document.getString(string.name)),
      Option(document.get(array.name, classOf[java.util.List[Int]])).map(_.asScala.toArray),
      Option(document.get(map.name, classOf[Document])).map(_.asScala.asInstanceOf[Map[String, Int]])
    )
  }

  override def toDocument(record: SimpleRecord): Document = {
    val document = new Document

    document.append(id.name, record.id)
    record.boolean.foreach(document.append(boolean.name, _))
    record.int.foreach(document.append(int.name, _))
    record.long.foreach(document.append(long.name, _))
    record.double.foreach(document.append(double.name, _))
    record.string.foreach(document.append(string.name, _))
    record.array.foreach(arrayVal => document.append(array.name, arrayVal.toBuffer.asJava))
    record.map.foreach(mapVal => {
      document.append(map.name, new Document(mapVal.asInstanceOf[Map[String, Object]].asJava))
    })

    document
  }
}

object TrivialORMQueryTest {

  val dbName = "test"

  trait Implicits extends Rogue {
    implicit def metaRecordToQuery[M <: TrivialORMMetaRecord[R], R <: TrivialORMRecord](
      meta: M with TrivialORMMetaRecord[R]
    ): Query[M, R, InitialState] = {
      Query(
        meta = meta,
        collectionName = meta.collectionName,
        lim = None,
        sk = None,
        maxScan = None,
        comment = None,
        hint = None,
        condition = AndCondition(Nil, None),
        order = None,
        select = None,
        readPreference = None
      )
    }
  }
}

// TODO(jacob): Move basically everything in the rogue tests into here.
class TrivialORMQueryTest extends RogueMongoTest
  with JUnitMustMatchers
  with BlockingResult.Implicits
  with TrivialORMQueryTest.Implicits {

  val queryOptimizer = new QueryOptimizer
  val serializer = new TrivialORMRogueSerializer

  val asyncCollectionFactory = new TrivialORMMongoCollectionFactory(asyncClientManager)
  val asyncClientAdapter = new AsyncMongoClientAdapter(asyncCollectionFactory, new TwitterFutureMongoCallbackFactory)
  val asyncQueryExecutor = new QueryExecutor(asyncClientAdapter, queryOptimizer, serializer)

  val blockingCollectionFactory = new TrivialORMMongoCollectionFactory(blockingClientManager)
  val blockingClientAdapter = new BlockingMongoClientAdapter(blockingCollectionFactory)
  val blockingQueryExecutor = new QueryExecutor(blockingClientAdapter, queryOptimizer, serializer)

  @Before
  override def initClientManagers(): Unit = {
    asyncClientManager.defineDb(
      SimpleRecord.mongoIdentifier,
      asyncMongoClient,
      TrivialORMQueryTest.dbName
    )
    blockingClientManager.defineDb(
      SimpleRecord.mongoIdentifier,
      blockingMongoClient,
      TrivialORMQueryTest.dbName
    )
  }

  @Test
  def canBuildQuery: Unit = {
    metaRecordToQuery(SimpleRecord).toString must_== """db.test_records.find({ })"""
    SimpleRecord.where(_.int eqs 1).toString must_== """db.test_records.find({ "int" : 1})"""
  }

  @Test
  def testAsyncCount: Unit = {
    val numInserts = 10
    val insertFuture = Futures.groupedCollect(1 to numInserts, numInserts)(_ => {
      asyncQueryExecutor.insert(SimpleRecord())
    })

    val idSelect = SimpleRecord.select(_.id)
    val testFuture = insertFuture.flatMap(_ => {
      Future.join(
        asyncQueryExecutor.count(idSelect).map(_ must_== 10),
        asyncQueryExecutor.count(idSelect.limit(3)).map(_ must_== 3),
        asyncQueryExecutor.count(idSelect.limit(15)).map(_ must_== 10),
        asyncQueryExecutor.count(idSelect.skip(5)).map(_ must_== 5),
        asyncQueryExecutor.count(idSelect.skip(12)).map(_ must_== 0),
        asyncQueryExecutor.count(idSelect.skip(3).limit(5)).map(_ must_== 5),
        asyncQueryExecutor.count(idSelect.skip(8).limit(4)).map(_ must_== 2)
      )
    })
    Await.result(testFuture)
  }

  @Test
  def testBlockingCount: Unit = {
    val numInserts = 10
    for (_ <- 1 to numInserts) {
      blockingQueryExecutor.insert(SimpleRecord())
    }

    val idSelect = SimpleRecord.select(_.id)
    blockingQueryExecutor.count(idSelect).unwrap must_== 10
    blockingQueryExecutor.count(idSelect.limit(3)).unwrap must_== 3
    blockingQueryExecutor.count(idSelect.limit(15)).unwrap must_== 10
    blockingQueryExecutor.count(idSelect.skip(5)).unwrap must_== 5
    blockingQueryExecutor.count(idSelect.skip(12)).unwrap must_== 0
    blockingQueryExecutor.count(idSelect.skip(3).limit(5)).unwrap must_== 5
    blockingQueryExecutor.count(idSelect.skip(8).limit(4)).unwrap must_== 2
  }

  // TODO(jacob): Uncomment and clean up these tests once their behavior is implemented.
  //
  // @Test
  // def canExecuteQuery: Unit = {
  //   executor.fetch(SimpleRecord.where(_.a eqs 1)) must_== Nil
  //   executor.count(SimpleRecord) must_== 0
  // }

  // @Test
  // def canUpsertAndGetResults: Unit = {
  //   executor.count(SimpleRecord) must_== 0

  //   executor.upsertOne(SimpleRecord.modify(_.a setTo 1).and(_.b setTo "foo"))

  //   executor.count(SimpleRecord) must_== 1

  //   val results = executor.fetch(SimpleRecord.where(_.a eqs 1))
  //   results.size must_== 1
  //   results(0).a must_== 1
  //   results(0).b must_== "foo"

  //   executor.fetch(SimpleRecord.where(_.a eqs 1).select(_.a)) must_== List(Some(1))
  //   executor.fetch(SimpleRecord.where(_.a eqs 1).select(_.b)) must_== List(Some("foo"))
  //   executor.fetch(SimpleRecord.where(_.a eqs 1).select(_.a, _.b)) must_== List((Some(1), Some("foo")))
  // }
}
