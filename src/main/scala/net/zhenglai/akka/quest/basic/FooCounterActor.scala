package net.zhenglai.akka.quest.basic

import akka.NotUsed
import akka.actor.{ Actor, ActorLogging }
import akka.http.scaladsl.model.ws
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse }
import akka.stream.scaladsl.{ Flow, Source }
import akka.util.ByteString

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


// Futures are composable
// val result = fromRedis recoverWith { case _ => fromScratch }
//  result.onFailure { ... } // error handling

// Why you shouldn't use Futures for concurrency: good luck controlling that.
class FooCounterActorMain extends App {

  type HttpServer = Flow[HttpRequest, HttpResponse, NotUsed]
  type HttpEntity = Source[ByteString, _]
  type WebSocketConnection = Flow[ws.Message, ws.Message, NotUsed]
}

