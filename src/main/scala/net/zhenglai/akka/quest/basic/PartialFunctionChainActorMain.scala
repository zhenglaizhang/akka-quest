package net.zhenglai.akka.quest.basic

import akka.actor.{ Actor, ActorLogging, ActorRef, ActorSystem, Props }

trait ProducerBehavior {
  this: Actor with ActorLogging =>
  val producerBehavior: Receive = {
    case GiveMeThings => sender() ! Give("thing")
  }
}

trait ConsumerBehavior {
  this: Actor with ActorLogging =>
  val consumerBehavior: Receive = {
    case ref: ActorRef =>
      // warning: actually, the ref might be un-initialized
      ref ! GiveMeThings
    case Give(thing) =>
      log.info("received: {}", thing)
  }
}

class Producer extends Actor with ActorLogging with ProducerBehavior {
  override def receive = producerBehavior
}

class Consumer extends Actor with ActorLogging with ConsumerBehavior {
  override def receive = consumerBehavior
}

class ProducerConsumer extends Actor with ActorLogging with ProducerBehavior with ConsumerBehavior {
  // Instead of inheritance the same pattern can be applied via composition - one would simply compose the receive method using partial functions from delegates.
  override def receive = producerBehavior orElse consumerBehavior
}

// protocol
case object GiveMeThings

final case class Give(thing: String)

object PartialFunctionChainActorMain extends App {
  val system = ActorSystem("PartialFunctionChainSystem")
  val actor = system.actorOf(Props(new ProducerConsumer), "producerConsumer")
  actor ! actor

  Thread.sleep(1000)
  system.terminate()
}
