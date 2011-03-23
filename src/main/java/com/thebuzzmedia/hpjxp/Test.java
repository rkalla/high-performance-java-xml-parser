package com.thebuzzmedia.hpjxp;

public class Test {
	public static void main(String[] args) {
		one();
		two();
		forTest();
	}
	
	public static void one() {
		char[] array = new char[10];
		array[0] = ' ';
		array[1] = ' ';
		array[2] = ' ';
		array[3] = ' ';
		array[4] = ' ';
		array[5] = 'h';
		array[6] = 'e';
		array[7] = 'l';
		array[8] = 'l';
		array[9] = 'o';
		System.out.println("Before [" + new String(array) + "]");
		System.arraycopy(array, 5, array, 0, 5);
		System.out.println(" After [" + new String(array) + "]");
	}
	
	public static void two() {
		char[] array = new char[10];
		array[0] = ' ';
		array[1] = ' ';
		array[2] = ' ';
		array[3] = 'w';
		array[4] = 'i';
		array[5] = 'l';
		array[6] = 'l';
		array[7] = 'i';
		array[8] = 'a';
		array[9] = 'm';
		System.out.println("\nBefore [" + new String(array) + "]");
		System.arraycopy(array, 3, array, 0, 7);
		System.out.println(" After [" + new String(array) + "]");
	}
	
	public static void forTest() {
		int i = 0;
		
		for(; i < 10; i++);
		
		System.out.println("i: " + i);
	}
}