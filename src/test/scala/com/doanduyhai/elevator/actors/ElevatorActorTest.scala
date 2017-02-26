package com.doanduyhai.elevator.actors

import akka.actor.{Props, ActorSystem}
import akka.testkit._
import com.typesafe.config.ConfigFactory
import org.scalatest._


class ElevatorActorTest extends TestKit(ActorSystem("ElevatorActorSystem",
    ConfigFactory.parseString("""akka.loggers = ["akka.testkit.TestEventListener"]"""))) with ImplicitSender
  with FlatSpecLike with Matchers with BeforeAndAfterAll {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }


  "ElevatorActor" should "move to target floor from initial pickup floor" in {
      //Given
      val controlSystem = TestProbe()
      val elevator = system.actorOf(Props(new ElevatorActor(1, controlSystem.ref, AtFloor(0))))

      //When
      elevator ! Pickup(Move(0, 3))

      //Then
      controlSystem.expectMsgAllOf(
        UpdateStatus(1, AtFloor(0), None),
        UpdateStatus(1, Move(0,3), None),
        UpdateStatus(1, Move(1,3), None),
        UpdateStatus(1, Move(2,3), None),
        UpdateStatus(1, AtFloor(3), None))
    }

  "ElevatorActor" should "move to target floor from floor higher than pickup floor" in {
      //Given
      val controlSystem = TestProbe()
      val elevator = system.actorOf(Props(new ElevatorActor(1, controlSystem.ref, AtFloor(5))))

      //When
      elevator ! Pickup(Move(3, 1))

      //Then
      controlSystem.expectMsgAllOf(
        UpdateStatus(1, AtFloor(5), None),
        UpdateStatus(1, Move(5,3), Some(Pickup(Move(3, 1)))),
        UpdateStatus(1, Move(4,3), Some(Pickup(Move(3, 1)))),
        UpdateStatus(1, AtFloor(3), Some(Pickup(Move(3, 1)))),
        UpdateStatus(1, Move(3,1), None),
        UpdateStatus(1, Move(2,1), None),
        UpdateStatus(1, AtFloor(1), None))
    }

  "ElevatorActor" should "move to target floor from floor lower than pickup floor" in {
      //Given
      val controlSystem = TestProbe()
      val elevator = system.actorOf(Props(new ElevatorActor(1, controlSystem.ref, AtFloor(1))))

      //When
      elevator ! Pickup(Move(4, 2))

      //Then
      controlSystem.expectMsgAllOf(
        UpdateStatus(1, AtFloor(1), None),
        UpdateStatus(1, Move(1,4), Some(Pickup(Move(4, 2)))),
        UpdateStatus(1, Move(2,4), Some(Pickup(Move(4, 2)))),
        UpdateStatus(1, Move(3,4), Some(Pickup(Move(4, 2)))),
        UpdateStatus(1, AtFloor(4), Some(Pickup(Move(4, 2)))),
        UpdateStatus(1, Move(4,2), None),
        UpdateStatus(1, Move(3,2), None),
        UpdateStatus(1, AtFloor(2), None))
    }

  "ElevatorActor" should "save pickup order and move to target floor when current move is finished" in {
      //Given
      val controlSystem = TestProbe()
      val elevator = system.actorOf(Props(new ElevatorActor(1, controlSystem.ref, Move(1, 3))))

      //When
      elevator ! Pickup(Move(5, 2))

      //Then
      controlSystem.expectMsgAllOf(
        UpdateStatus(1, Move(1, 3), None),
        UpdateStatus(1, Move(1, 3), Some(Pickup(Move(5, 2)))),
        UpdateStatus(1, Move(2, 3), Some(Pickup(Move(5, 2)))),
        UpdateStatus(1, AtFloor(3), Some(Pickup(Move(5, 2)))),
        UpdateStatus(1, Move(3, 5), Some(Pickup(Move(5, 2)))),
        UpdateStatus(1, Move(4, 5), Some(Pickup(Move(5, 2)))),
        UpdateStatus(1, AtFloor(5), Some(Pickup(Move(5, 2)))),
        UpdateStatus(1, Move(5, 2), None),
        UpdateStatus(1, Move(4, 2), None),
        UpdateStatus(1, Move(3, 2), None),
        UpdateStatus(1, AtFloor(2), None))
    }

  "ElevatorActor" should "write error message when already has scheduled order" in {
      //Given
      val controlSystem = TestProbe()
      val elevator = system.actorOf(Props(new ElevatorActor(1, controlSystem.ref, Move(1, 3), scheduledOrder = Option(Pickup(Move(5,2))))))

      //Then
      EventFilter.error(message = s"Cannot accept Pickup(Move(3,0)) " +
        s"because the elevator is moving right now and a pickup Pickup(Move(5,2)) is already scheduled", occurrences = 1) intercept {
        elevator ! Pickup(Move(3, 0))
      }
    }

  "ElevatorActor" should "execute simulation with initial Move state and no scheduled order" in {
      //Given
      val controlSystem = TestProbe()
      val elevator = system.actorOf(Props(new ElevatorActor(1, controlSystem.ref, Move(0, 2))))

      //When
      elevator ! StartSimulation

      //Then
      controlSystem.expectMsgAllOf(
        UpdateStatus(1, Move(0, 2), None),
        UpdateStatus(1, Move(1, 2), None),
        UpdateStatus(1, AtFloor(2), None))

    }

  "ElevatorActor" should "execute simulation with initial Move state and scheduled order" in {
      //Given
      val controlSystem = TestProbe()
      val elevator = system.actorOf(Props(new ElevatorActor(1, controlSystem.ref, Move(1, 3),
        scheduledOrder = Some(Pickup(Move(5,2))))))

      //When
      elevator ! StartSimulation

      //Then
      controlSystem.expectMsgAllOf(
        UpdateStatus(1, Move(1, 3), Some(Pickup(Move(5,2)))),
        UpdateStatus(1, Move(2, 3), Some(Pickup(Move(5,2)))),
        UpdateStatus(1, AtFloor(3), Some(Pickup(Move(5,2)))),
        UpdateStatus(1, Move(3, 5), Some(Pickup(Move(5,2)))),
        UpdateStatus(1, Move(4, 5), Some(Pickup(Move(5,2)))),
        UpdateStatus(1, AtFloor(5), Some(Pickup(Move(5,2)))),
        UpdateStatus(1, Move(5, 2), None),
        UpdateStatus(1, Move(4, 2), None),
        UpdateStatus(1, Move(3, 2), None),
        UpdateStatus(1, AtFloor(2), None))
    }

  "ElevatorActor" should "execute simulation with AfFloor state and scheduled order" in {
      //Given
      val controlSystem = TestProbe()
      val elevator = system.actorOf(Props(new ElevatorActor(1, controlSystem.ref, AtFloor(3),
        scheduledOrder = Some(Pickup(Move(5,2))))))

      //When
      elevator ! StartSimulation

      //Then
      controlSystem.expectMsgAllOf(
        UpdateStatus(1, AtFloor(3), Some(Pickup(Move(5,2)))),
        UpdateStatus(1, Move(3, 5), Some(Pickup(Move(5,2)))),
        UpdateStatus(1, Move(4, 5), Some(Pickup(Move(5,2)))),
        UpdateStatus(1, AtFloor(5), Some(Pickup(Move(5,2)))),
        UpdateStatus(1, Move(5, 2), None),
        UpdateStatus(1, Move(4, 2), None),
        UpdateStatus(1, Move(3, 2), None),
        UpdateStatus(1, AtFloor(2), None))
    }

  "ElevatorActor" should "execute simulation with AfFloor state and no scheduled order" in {
      //Given
      val controlSystem = TestProbe()
      val elevator = system.actorOf(Props(new ElevatorActor(1, controlSystem.ref, AtFloor(3))))

      //Then
      EventFilter.info(message = "No order to execute", occurrences = 1) intercept {
        elevator ! StartSimulation
      }
    }
}
