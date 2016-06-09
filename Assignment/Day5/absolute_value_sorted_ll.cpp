//
// Created by Rupesh Choudhary on 6/9/2016.
//

#include<iostream>
#include <cstdlib>

using namespace std;

struct node {
    int data;
    struct node *next;
};

void insertFirst(struct node **head, int new_data) {
    struct node *new_node = (struct node *) malloc(sizeof(struct node));

    new_node->data = new_data;
    new_node->next = (*head);
    (*head) = new_node;
}

void printList(struct node *head) {
    while (head != NULL) {
        cout << head->data << "-->";
        head = head->next;
    }
}


void sortList(node **head) {
    node *prev = (*head);
    node *curr = (*head)->next;

    while (curr != NULL) {
        if (curr->data < prev->data) {
            prev->next = curr->next; //delete

            curr->next = (*head); //insert at head
            (*head) = curr;

            curr = prev;
        }
        else
            prev = curr;

        curr = curr->next;
    }
}

int main() {
    node *head = NULL;
    insertFirst(&head, -5);
    insertFirst(&head, 5);
    insertFirst(&head, 4);
    insertFirst(&head, 3);
    insertFirst(&head, -2);
    insertFirst(&head, 1);
    insertFirst(&head, 0);

    sortList(&head);
    printList(head);

    return 0;
}