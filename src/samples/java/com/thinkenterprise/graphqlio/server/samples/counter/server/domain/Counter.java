package com.thinkenterprise.graphqlio.server.samples.counter.server.domain;

public class Counter {

	private int value = 0;

	public int getValue() {
		return value;
	}

	public void setValue(int value) {
		this.value = value;
	}

	public void inc() {
		this.value++;
	}

}
