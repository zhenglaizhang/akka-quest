package net.zhenglai.akka.quest.basic

import akka.actor.{ Actor, ActorLogging, ActorSystem, Kill, Props }

class SupervisionActor extends Actor with ActorLogging {
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
