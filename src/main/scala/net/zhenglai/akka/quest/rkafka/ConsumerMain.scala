package net.zhenglai.akka.quest.rkafka

import java.util.concurrent.atomic.AtomicLong

import scala.concurrent.Future

import akka.Done
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
