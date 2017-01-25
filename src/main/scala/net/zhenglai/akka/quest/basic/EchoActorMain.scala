package net.zhenglai.akka.quest.basic

import scala.concurrent.Await
import scala.concurrent.duration._

import akka.actor.{ ActorSystem, PoisonPill, Props }
import akka.event.Logging
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import net.zhenglai.akka.quest.basic.MagicNumberActor.{ Goodbye, Greeting }


object EchoActorMain {

  def main(args: Array[String]): Unit = {
    val system = ActorSystem("mySystem")

    val log = Logging(system, getClass)
    implicit val timeout = Timeout(1 second)
    implicit val ec = system.dispatcher
    // Actors are created by passing a Props instance into the actorOf factory method
    // which is available on ActorSystem and ActorContext.

    // top level actor
    // supervised by the actor system's provided guardian actor
    // return ActorRef: handle to the actor instance & the only way to interact with it
    // Actors are automatically started asynchronously when created.
    // root guardian (which is the parent of "/user")
    // the .. in actor paths here always means the logical structure, i.e. the supervisor.
    // [akka://mySystem/user/echoActor]
    val actor = system.actorOf(Props[EchoActor], name = "echoActor")

    // The ActorRef is immutable and has a one to one relationship with the Actor it represents.
    // The ActorRef is also serializable and network-aware.


    // ! means “fire-and-forget”, e.g. send a message asynchronously and return immediately. Also known as tell.
    // ? sends a message asynchronously and returns a Future representing a possible reply. Also known as ask
    //  performance implications of using ask since something needs to keep track of when it times out,
    // there needs to be something that bridges a Promise into an ActorRef and it also needs to be reachable through remoting.
    // So always prefer tell for performance, and only ask if you must.
    // Message ordering is guaranteed on a per-sender basis.
    actor ! "unknown"

    // The actor that’s called should send a reply back using the ! method,
    // The ask operation involves creating an internal actor for handling this reply,
    // which needs to have a timeout after which it is destroyed in order not to leak resources; see more below.
    val f = actor ? "ping"
    // [EchoActorMain$(akka://mySystem)] response of ask ping: pong
    log.info(s"1st response of ask ping: ${Await.result(f, timeout.duration).asInstanceOf[String]}")


    // When you need to perform work like this, the mantra is, “Delegate, delegate, delegate.”
    val f2 = (actor ? "ping").mapTo[String]
    log.info(s"2nd response of ask ping: ${Await.result(f2, timeout.duration)}")
    // install onComplete handler
    f2.pipeTo(actor)
    f2 pipeTo actor
    pipe(f) to actor


    // tell => fire and forget
    //  No blocking waiting for a message.
    // This gives the best concurrency and scalability characteristics.
    // If invoked from within an Actor, then the sending actor reference will be implicitly passed along with the message
    actor ! Greeting("Zhenglai")
    actor ! 0

    // It is important to note that Actors do not stop automatically when no longer referenced, every Actor that is created must also explicitly be destroyed.
    // The only simplification is that stopping a parent Actor will also recursively stop all the child Actors that this parent has created.


    // When writing code outside of actors which shall communicate with actors,
    // the ask pattern can be a solution

    val magicActorOut = system.actorOf(MagicNumberActor.props(9999), "magicActorOut")
    magicActorOut ! "unhandled"

    /*
    It is always preferable to communicate with other Actors using their ActorRef instead of relying upon ActorSelection. Exceptions are

      * sending messages using the At-Least-Once Delivery facility
      * initiating first contact with a remote system
In all other cases ActorRefs can be provided during Actor creation or initialization, passing them from parent to child or introducing Actors by sending their ActorRefs to other Actors within messages.

      // will look up all siblings beneath same supervisor
     */
    //    context.actorSelection("../*")

    val theActor = system.actorSelection("/user/magicActorOut")
    theActor ! -100


    val ghost = system.actorSelection("/user/ghost")
    ghost ! "wow" // should be dropped & sent to dead letters

    // remote actor address lookup
    // context.actorSelection("akka.tcp://app@otherhost:1234/user/serviceB")

    val followerActor = system.actorOf(FollowerActor.props)

    // ActorNotFound if failure or identification times out
    theActor.resolveOne().map(_ ! -200)
    // There is an implicit conversion from inbox to actor reference which means that in this example the sender reference will be that of the actor hidden away within the inbox

    // TODO: http://doc.akka.io/docs/akka/2.4/scala/actors.html#Forward_message
    //    implicit val inbox = new Inbox[Int]("inbox")
    //    magicActorOut ! 1
    //    Thread.sleep(1 * 1000)
    //    require(inbox.receiveMsg() == 10000)
    actor ! PoisonPill

    actor ! Goodbye
    Thread.sleep(1 * 1000)

    val watchActor = system.actorOf(WatchActor.props(actor))
    Thread.sleep(200)

    // TODO: how to terminate actor system gracefully
    system.terminate()
    Await.ready(system.whenTerminated, 10.seconds)
  }
}
