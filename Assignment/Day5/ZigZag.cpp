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

void zigZag(node *head) {
    bool flag = true;

    node *current = head;
    while (current->next != NULL) {
        if (flag)  //"<"
        {
            if (current->data > current->next->data)
                swap(current->data, current->next->data);
        }
        else // >
        {
            if (current->data < current->next->data)
                swap(current->data, current->next->data);
        }

        current = current->next;
        flag = !flag;
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

    zigZag(head);
    printList(head);

    return 0;
}

