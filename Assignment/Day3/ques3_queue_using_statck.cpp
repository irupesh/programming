//
// Created by Rupesh Choudhary on 6/7/2016.
//
#include<iostream>

using namespace std;

int stack1[100], stack2[100];
int top1 = -1, top2 = -1;
int count = 0;

void create() //ceate queue
{
    top1 = top2 = -1;
}

void push1(int data)  //push data to stack 1
{
    stack1[++top1] = data;
}

int pop1() {
    return (stack1[top1--]);
}

void push2(int data) {
    stack2[++top2] = data;
}

int pop2() {
    return (stack2[top2--]);
}

void enqueue(int data) {
    push1(data);
    count++;
}

int dequeue() {
    int i, data;

    for (i = 0; i < count; i++) {
        push2(pop1());
    }
    data = pop2();
    count--;
    for (i = 0; i < count; i++) {
        push1(pop2());
    }
    return data;
}

int main() {

    create();

    enqueue(2);
    enqueue(5);
    enqueue(4);


    cout << dequeue() << endl;
    cout << dequeue() << endl;
    cout << dequeue() << endl;


}