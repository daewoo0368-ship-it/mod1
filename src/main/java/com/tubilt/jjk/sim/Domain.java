package com.tubilt.jjk.sim;

/** 기계 간 연결(포트)의 종류. 같은 종류의 포트끼리만 이어지고, 종류마다 별도의 네트워크 솔버가 계산한다. */
public enum Domain { MECHANICAL, FLUID, THERMAL, ELECTRICAL, CONTROL }
