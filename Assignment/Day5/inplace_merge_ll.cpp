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


struct node *SortedMerge(struct node *ll1, struct node *ll2) {
    struct node *result = NULL;

    if (ll1 == NULL)
        return (ll2);
    else if (ll2 == NULL)
        return (ll1);

    if (ll1->data <= ll2->data) {
        result = ll1;
        result->next = SortedMerge(ll1->next, ll2);
    }
    else {
        result = ll2;
        result->next = SortedMerge(ll1, ll2->next);
    }
    return (result);
}

int main() {
    struct node *temp = NULL;
    struct node *a = NULL;
    struct node *b = NULL;

    insertFirst(&a, 15);
    insertFirst(&a, 10);
    insertFirst(&a, 5);
    insertFirst(&b, 20);
    insertFirst(&b, 3);
    insertFirst(&b, 2);

    temp = SortedMerge(a, b);
    printList(temp);

    return 0;
}

