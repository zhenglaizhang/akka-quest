package net.zhenglai.akka.quest.basic

import akka.actor.{ Actor, ActorLogging }

class FooCounterActor extends Actor with ActorLogging {
  // Actors are for state
  // safe to use primitive here
  // If we use Future, we need to use concurrency primitives e.g. AtomicLong
  //   and contention might happen
  // though it appears public, it's in fact private, the actual FooCounter object is
  // never exposed to the outside world, only an ActorRef object is
  var count: Long = 0

  override def receive = {
    case Foo => count += 1
    case FooCounterRequest => sender ! count
  }

  object Foo

  object FooCounterRequest

}

// Caching is not state
// if an actor is used for caching, a single thread for both reads and writes becomes absolutely necessary which is less optimal compared with Future caching

class FooCounterActorMain extends App {

}

