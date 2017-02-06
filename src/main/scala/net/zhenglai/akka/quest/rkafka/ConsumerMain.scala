package net.zhenglai.akka.quest.rkafka

import java.util.concurrent.atomic.AtomicLong

import scala.concurrent.Future
import scala.concurrent.duration._

import akka.Done
import akka.kafka.ConsumerMessage.CommittableOffsetBatch
import akka.kafka.Subscriptions
import akka.kafka.scaladsl.Consumer
import akka.stream.scaladsl.Sink
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition

class DB {
  private val offset = new AtomicLong

  // The primary use case for this is allowing the application to
  // store both the offset and the results of the consumption in the
  // same system in a way that both the results and offsets are stored atomically.
  def save(record: ConsumerRecord[Array[Byte], String]): Future[Done] = {
    println(s"DB.save: ${record.value}:${record.offset}")
    offset.set(record.offset)
    Future.successful(Done)
  }

  def loadOffset(): Future[Long] = Future.successful(offset.get)

  def update(data: String): Future[Done] = {
    println(s"DB.update: $data")
    Future.successful(Done)
  }
}

class Rocket {
  def launch(destination: String): Future[Done] = {
    println(s"Rocket launched to $destination")
    Future.successful(Done)
  }
}

object ConsumerMain extends App with Environment {

  //  externalOffsetStorageDemo()
  //  offsetStorageInKafka()
  //  batchCommitOffset2()
  atMostOnce()


  private[this] def batchCommitOffset() = {
    val db = new DB
    val done = Consumer.committableSource(
      consumerSettings.withGroupId("gid6"),
      Subscriptions.topics("topic1")
    )
      .mapAsync(1) { msg =>
        db.update(msg.record.value).map(_ => msg.committableOffset)
      }
      // it will only aggregate elements into batches if
      // the downstream consumer is slower than the upstream producer.
      .batch(max = 20, first => CommittableOffsetBatch.empty.updated(first)) { (batch, elem) => batch.updated(elem) }
      .mapAsync(3) {_.commitScaladsl()}
      .runWith(Sink.ignore)
  }

  private[this] def atMostOnce() = {
    // If you commit the offset before processing the message
    // you get “at-most once delivery” semantics, and for that there is a
    // Consumer.atMostOnceSource. However, atMostOnceSource commits the offset
    // for each message and that is rather slow, batching of commits is recommended.
    val rocket = new Rocket
    val done = Consumer.atMostOnceSource(
      consumerSettings.withGroupId("gid8"),
      Subscriptions.topics("topic1")
    )
      .mapAsync(1) { record =>
        rocket.launch(record.value)
      }
      .runWith(Sink.ignore)
  }

  private[this] def batchCommitOffset2() = {
    val db = new DB
    val done = Consumer.committableSource(
      consumerSettings.withGroupId("gid7"),
      Subscriptions.topics("topic1")
    )
      .mapAsync(1) { msg =>
        db.update(msg.record.value).map(_ => msg.committableOffset)
      }
      .groupedWithin(10, 5 seconds)
      .map(g => g.foldLeft(CommittableOffsetBatch.empty) { (batch, elem) => batch.updated(elem) })
      .mapAsync(3) {_.commitScaladsl()}
      .runWith(Sink.ignore)
  }

  private[this] def offsetStorageInKafka() = {

    // Compared to auto-commit this gives exact control of when
    // a message is considered consumed.

    // This is useful when “at-least once delivery” is desired,
    // as each message will likely be delivered one time but in
    // failure cases could be duplicated.
    val db = new DB
    val done = Consumer.committableSource(
      consumerSettings.withGroupId("gid5"),
      Subscriptions.topics("topic1")
    )
      .mapAsync(1) { msg =>
        println(s"consuming ${msg.record.value}:${msg.committableOffset}")
        db.update(msg.record.value).map(_ => msg)
      }
      .mapAsync(1) { msg =>
        msg.committableOffset.commitScaladsl()
      }
      .runWith(Sink.ignore)
    // The above example uses separate mapAsync stages for processing and committing.
    // This guarantees that for parallelism higher than 1 we will keep correct
    // ordering of messages sent for commit.
  }

  def externalOffsetStorageDemo() = {
    val db = new DB
    db.loadOffset().foreach { fromOffset =>
      val partition = 0
      // Note how the starting point (offset) is assigned for a
      // given consumer group id, topic and partition
      val subscription = Subscriptions.assignmentWithOffset(
        new TopicPartition("topic1", partition) -> fromOffset
      )

      val done = Consumer.plainSource(consumerSettings.withGroupId("gid4"), subscription)
        .map { record =>
          println(record.value)
          record
        }
        .mapAsync(1)(db.save)
        .runWith(Sink.ignore)
    }
  }
}
