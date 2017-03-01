package com.doanduyhai.elevator.actors

import java.io.{PrintStream, ByteArrayOutputStream}

import akka.actor.{ActorSystem}
import akka.testkit._
import com.typesafe.config.ConfigFactory
import org.scalatest.{FlatSpecLike, BeforeAndAfterAll, Matchers}

import scala.collection.immutable.Queue


class ControlSystemActorTest extends TestKit(ActorSystem("ControlSystemActorSystem",
  ConfigFactory.parseString("""akka.loggers = ["akka.testkit.TestEventListener"]"""))) with ImplicitSender
  with FlatSpecLike with Matchers with BeforeAndAfterAll {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "ControlSystemActor" should "update elevator status map but do not start processing" in {
    //Given
    val controlSystem = TestActorRef(new ControlSystemActor(2))
    val underlyingActor = controlSystem.underlyingActor

    //When
    controlSystem ! UpdateStatus(1, Move(3,4), Some(Pickup(Move(1,8))))

    //Then
    underlyingActor.elevatorsStatus shouldBe(Map(1 -> (Move(3,4),Some(Pickup(Move(1,8))))))
    underlyingActor.elevatorById shouldBe(Map(1 -> testActor))
    underlyingActor.orderQueue shouldBe(Queue.empty[Pickup])
  }

  "ControlSystemActor" should "display system on elevator status update" in {
    //Given
    val baos = new ByteArrayOutputStream()
    val controlSystem = TestActorRef(new ControlSystemActor(2, printStream = new PrintStream(baos)))

    //When
    controlSystem ! UpdateStatus(1, Move(3,4), Some(Pickup(Move(1,8))))
    controlSystem ! UpdateStatus(2, Move(0,2), None)

    //Then
    val display = new String(baos.toByteArray())

    display should include(
      """
        |    Control system orders queue: [].
        |
        |--------------------------------------------------
        |1[1->8]:  _  _  _ |3>{4}
        |2[    ]: |0> _ {2}
        |--------------------------------------------------""".stripMargin)
  }

  "ControlSystemActor" should "push queue order to a free elevator" in {
    //Given
    val elevator1 = TestProbe()
    val elevator2 = TestProbe()
    val baos = new ByteArrayOutputStream()
    val controlSystem = TestActorRef(new ControlSystemActor(2, orderQueue = Queue(Pickup(Move(1,3))), printStream = new PrintStream(baos)))
    val underlyingActor = controlSystem.underlyingActor

    //When
    elevator1.send(controlSystem, UpdateStatus(1, Move(3,4), Some(Pickup(Move(1,8)))))
    elevator2.send(controlSystem, UpdateStatus(2, AtFloor(3), None))

    //Then
    elevator2.expectMsg(Pickup(Move(1,3)))
    underlyingActor.elevatorsStatus(2) shouldBe((Move(3,1), Some(Pickup(Move(1,3)))))
    val display = new String(baos.toByteArray())

    display should include(
      """
        |    Control system orders queue: [Move(1,3)].
        |
        |--------------------------------------------------
        |1[1->8]:  _  _  _ |3>{4}
        |2[    ]:  _  _  _ |3|
        |--------------------------------------------------""".stripMargin)
    display should include("Send queued pickup order: Move(1,3) to elevator 2")
  }

  "ControlSystemActor" should "push queue order to moving elevator with no scheduled order" in {
    //Given
    val elevator1 = TestProbe()
    val elevator2 = TestProbe()
    val baos = new ByteArrayOutputStream()
    val controlSystem = TestActorRef(new ControlSystemActor(2, orderQueue = Queue(Pickup(Move(1,3))), printStream = new PrintStream(baos)))
    val underlyingActor = controlSystem.underlyingActor

    //When
    elevator1.send(controlSystem, UpdateStatus(1, Move(3,4), Some(Pickup(Move(1,8)))))
    elevator2.send(controlSystem, UpdateStatus(2, Move(0, 4), None))

    //Then
    elevator2.expectMsg(Pickup(Move(1,3)))
    underlyingActor.elevatorsStatus(2) shouldBe((Move(0,4), Some(Pickup(Move(1,3)))))

    val display = new String(baos.toByteArray())

    display should include(
      """
        |    Control system orders queue: [Move(1,3)].
        |
        |--------------------------------------------------
        |1[1->8]:  _  _  _ |3>{4}
        |2[    ]: |0> _  _  _ {4}
        |--------------------------------------------------""".stripMargin)
    display should include("Send queued pickup order: Move(1,3) to elevator 2")
  }

  "ControlSystemActor" should "not dequeue order when no elevator available" in {
    //Given
    val elevator1 = TestProbe()
    val elevator2 = TestProbe()
    val controlSystem = TestActorRef(new ControlSystemActor(2, orderQueue = Queue(Pickup(Move(1,3)))))
    val underlyingActor = controlSystem.underlyingActor

    //When
    elevator1.send(controlSystem, UpdateStatus(1, Move(3,4), Some(Pickup(Move(1,8)))))
    elevator2.send(controlSystem, UpdateStatus(2, Move(0,4), Some(Pickup(Move(5,3)))))

    //Then
    underlyingActor.orderQueue shouldBe(Queue(Pickup(Move(1,3))))
  }

  "ControlSystemActor" should "dispatch pickup order to an available elevator" in {
    //Given
    val elevator1 = TestProbe()
    val controlSystem = TestActorRef(new ControlSystemActor(1))
    val underlyingActor = controlSystem.underlyingActor
    elevator1.send(controlSystem, UpdateStatus(1, AtFloor(0), None))

    //When
    controlSystem ! Pickup(Move(6,4))

    //Then
    elevator1.expectMsg(Pickup(Move(6,4)))
    underlyingActor.orderQueue shouldBe(Queue.empty[Pickup])
    underlyingActor.elevatorsStatus shouldBe(Map(1 -> (Move(0,6), Some(Pickup(Move(6,4))))))
  }


  "ControlSystemActor" should "dispatch pickup order to the nearest idle elevators" in {
    //Given
    val elevator1 = TestProbe()
    val elevator2 = TestProbe()
    val controlSystem = TestActorRef(new ControlSystemActor(1))
    val underlyingActor = controlSystem.underlyingActor
    elevator1.send(controlSystem, UpdateStatus(1, AtFloor(1), None))
    elevator2.send(controlSystem, UpdateStatus(2, AtFloor(6), None))

    //When
    controlSystem ! Pickup(Move(4,1))

    //Then
    elevator2.expectMsg(Pickup(Move(4,1)))
    underlyingActor.elevatorsStatus shouldBe(Map(1 -> (AtFloor(1), None), 2 -> (Move(6,4), Some(Pickup(Move(4,1))))))
  }


  "ControlSystemActor" should "dispatch pickup order to the nearest moving elevators case 1" in {
    //Given
    val elevator1 = TestProbe()
    val elevator2 = TestProbe()
    val controlSystem = TestActorRef(new ControlSystemActor(1))
    val underlyingActor = controlSystem.underlyingActor
    elevator1.send(controlSystem, UpdateStatus(1, Move(1,3), None))
    elevator2.send(controlSystem, UpdateStatus(2, Move(1,4), None))

    //When
    controlSystem ! Pickup(Move(4,1))

    //Then
    elevator2.expectMsg(Pickup(Move(4,1)))
    underlyingActor.elevatorsStatus shouldBe(Map(1 -> (Move(1,3), None), 2 -> (Move(1,4), Some(Pickup(Move(4,1))))))
  }


  "ControlSystemActor" should "dispatch pickup order to the nearest moving elevators case 2" in {
    //Given
    val elevator1 = TestProbe()
    val elevator2 = TestProbe()
    val controlSystem = TestActorRef(new ControlSystemActor(1))
    val underlyingActor = controlSystem.underlyingActor
    elevator1.send(controlSystem, UpdateStatus(1, Move(1,5), None)) //Current path = 4, Next path (|4-5| + 1) = 2, Total = 6
    elevator2.send(controlSystem, UpdateStatus(2, Move(9,4), None)) //Current path = 5, Next path |4-4| = 0, Total = 5

    //When
    controlSystem ! Pickup(Move(4,1))

    //Then
    elevator2.expectMsg(Pickup(Move(4,1)))
    underlyingActor.elevatorsStatus shouldBe(Map(1 -> (Move(1,5), None), 2 -> (Move(9,4), Some(Pickup(Move(4,1))))))
  }

  "ControlSystemActor" should "enqueue pickup order because no available elevator" in {
    //Given
    val elevator1 = TestProbe()
    val controlSystem = TestActorRef(new ControlSystemActor(1))
    val underlyingActor = controlSystem.underlyingActor
    elevator1.send(controlSystem, UpdateStatus(1, Move(0,1), Some(Pickup(Move(1,4)))))

    //When
    controlSystem ! Pickup(Move(6,4))

    //Then
    underlyingActor.orderQueue shouldBe(Queue(Pickup(Move(6,4))))
    underlyingActor.elevatorsStatus shouldBe(Map(1 -> (Move(0,1), Some(Pickup(Move(1,4))))))
  }

  "ControlSystemActor" should "enqueue pickup order" in {
    //Given
    val controlSystem = TestActorRef(new ControlSystemActor(2, orderQueue = Queue(Pickup(Move(1,3)))))
    val underlyingActor = controlSystem.underlyingActor

    //When
    controlSystem ! Pickup(Move(6,4))

    //Then
    underlyingActor.orderQueue shouldBe(Queue(Pickup(Move(1,3)), Pickup(Move(6,4))))
  }

  "ControlSystemActor" should "raise error when order queue is full" in {
    //Given
    val controlSystem = TestActorRef(new ControlSystemActor(2, orderQueue = Queue(Pickup(Move(1,3))), maxQueueSize = 1))
    val pickup: Pickup = Pickup(Move(6, 4))

    //Then
    EventFilter.error(message = s"Cannot enqueue order $pickup because the queue is full", occurrences = 1) intercept {
      controlSystem ! pickup
    }
  }
}
