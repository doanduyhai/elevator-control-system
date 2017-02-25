package com.doanduyhai.elevator.actors

import org.scalatest.{FlatSpec, Matchers}

class ElevatorStatusTest extends FlatSpec with Matchers {

  "Move" should "move up next step" in {
    val move: Move = Move(0, 5)
    val next = move.nextStep

    next shouldBe Move(1, 5)
    next.nextStep shouldBe Move(2, 5)
  }

  "Move" should "move down next step" in {
    val move: Move = Move(4, 1)
    val next = move.nextStep

    next shouldBe Move(3, 1)
    next.nextStep shouldBe Move(2, 1)
  }

  "Move" should "stay still next step" in {
    val move: Move = Move(0, 1)
    val next = move.nextStep

    next shouldBe AtFloor(1)
    next.nextStep shouldBe AtFloor(1)
  }

  "Move" should "become still if current floor == target floor" in {
    val move: Move = Move(1, 1)
    val next = move.nextStep

    next shouldBe AtFloor(1)
  }

  "Still" should "stay still next step" in {
    val still = AtFloor(7)
    val next = still.nextStep

    next shouldBe AtFloor(7)
  }

  "Move" should "throw exception for negative floor" in {
    val ex = intercept[IllegalArgumentException] {
      Move(-1, 3)
    }
    ex.getMessage shouldBe "Invalid negative floor"
  }
}
