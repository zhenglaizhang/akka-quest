package net.zhenglai.akka.quest.basic

import akka.actor.{ Actor, ActorLogging, ActorSystem, Kill, OneForOneStrategy, Props }
import scala.concurrent.duration._

import akka.actor.SupervisorStrategy.{ Escalate, Restart, Resume, Stop }

// each actor is the supervisor of its children, and as such each actor defines fault handling supervisor strategy.
// This strategy cannot be changed afterwards as it is an integral part of the actor system’s structure

class SupervisionActor extends Actor with ActorLogging {

  /*
   one-for-one strategy, meaning that each child is treated separately (an all-for-one strategy works very similarly,
   the only difference is that any decision is applied to all children of the supervisor, not only the failing one).
   There are limits set on the restart frequency, namely maximum 10 restarts per minute; each of these settings could be left out,
   which means that the respective limit does not apply, leaving the possibility to specify an absolute upper limit on the restarts or to make the restarts work infinitely. The child actor is stopped if the limit is exceeded.
   */
  override def supervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 10, withinTimeRange = 1 minute) {
      case _: ArithmeticException => Resume
      case _: NullPointerException => Restart
      case _: IllegalArgumentException => Stop
      case _: Exception => Escalate
    }

  // Receive: type alias for PartialFunction[Any, Unit]
  override def receive: Receive = {
    case "error" => throw new RuntimeException("error")
    case msg => log.info("received: {}", msg)
  }
}

object SupervisionActorMain extends App {

  val system = ActorSystem("supervisionActorSystem")
  val actor = system.actorOf(Props[SupervisionActor], name = "supervisionActor")
  actor ! "wow"

  //  If an exception is thrown while a message is being processed (i.e. taken out of its mailbox and handed over to the current behavior), then this message will be lost.
  // So if you want to retry processing of a message, you need to deal with it yourself by catching the exception and retry your flow.
  // Make sure that you put a bound on the number of retries since you don't want a system to livelock (so consuming a lot of cpu cycles without making progress).
  // Another possibility would be to have a look at the PeekMailbox pattern.


  // If an exception is thrown while a message is being processed, nothing happens to the mailbox. If the actor is restarted, the same mailbox will be there.
  actor ! "error"

  // cause the actor to throw a ActorKilledException, triggering a failure.
  // If code within an actor throws an exception, the actor will suspend operation and its supervisor will be asked how to handle the failure, which may mean resuming the actor, restarting it or terminating it completely.
  actor ! Kill

  Thread.sleep(1000L)
  system.terminate()
}
