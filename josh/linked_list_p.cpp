//
// Created by Rupesh Choudhary on 8/19/2016.
//

#include<iostream>
using namespace std;

typedef struct node{
    int data;
    struct node *next;
} Node;

Node* addbeg(Node *head,int data){
    Node* tnode = new Node;
    tnode->data = data;
    tnode->next = NULL;

    if(head == NULL){
        head = tnode;
        return head;
    }
    else{
        tnode->next = head;
        head = tnode;
        return head;
    }
}

Node* addPos(Node *head,int data,int pos){
    Node* curr = head;
    int count=1;

    Node* tnode = new Node;
    tnode->data = data;
    tnode->next = NULL;

    if(pos == 0){
        tnode->next = head;
        head = tnode;
        return head;
    }

    while(count<pos){
        curr = curr->next;
        count++;
        if(curr == NULL){
            cout<<"Insersion Not possible"<<endl;
            return head;
        }
    }

    tnode->next = curr->next;
    curr->next = tnode;
    return head;
}

Node* reverseList(Node* head){

    if(!head){
        cout<<"LIST IS EMPTY.. OPERATION IS NOT POSSIBLE.!";
        return head;
    }else{
        Node *curr = head,*prev = NULL,*next;
        while(curr){
            next = curr->next;
            curr->next = prev;
            prev = curr;
            curr = next;
        }
        return head = prev;

    }
}

Node* delNodeByData(Node* head,int data){

    while(head && head->data == data){
        Node* temp = head;
        head = head->next;
        delete(temp);
    }

    if(head) {
        Node *curr = head->next;
        Node *prev = head;

        while (curr != NULL) {
            if (curr->data == data) {
                Node *temp = curr;
                prev->next = curr->next;
                curr = prev->next;
                delete (temp);
            } else {
                prev = prev->next;
                curr = curr->next;
            }
        }
    }
    return head;
}

void display(Node* head){
    Node* temp = head;

    if(!head){
        cout<<"List is empty";
        return;
    }

    while(temp != NULL){
        cout<<temp->data<<"  ";
        temp = temp->next;
    }
    cout<<"\n";
}

int main(){
    Node *head = NULL;

    head = addbeg(head,3);
    head = addbeg(head,7);
    head = addbeg(head,12);
    head = addbeg(head,25);
    head = addbeg(head,9);
    /*
    head = delNodeByData(head,5);
    head = addPos(head, 100,0);
    head = addPos(head, 85,7);
     */
    display(head);
    head = reverseList(head);
    display(head);
}