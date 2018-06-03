//
// Created by Rupesh Choudhary on 8/22/2016.
//
#include<iostream>
using namespace std;

struct node{
    int data;
    node *lchild,*rchild;
};

node* createNode(int data,node* lchild = NULL,node* rchild = NULL){
    node *temp = new node;
    temp->data = data;
    temp->lchild = lchild;
    temp->rchild = rchild;
    return temp;
}

void inoder(node* root){
    if(!root)
        return;
    else{
        inoder(root->lchild);
        cout<<root->data<<" ";
        inoder(root->rchild);
    }
}

int main(){
    node* root = createNode(5);
    root->lchild = createNode(8);
    root->rchild = createNode(12);
    root->lchild->rchild = createNode(13);
    root->lchild->lchild = createNode(85);
    root->rchild->lchild = createNode(32);
    root->rchild->rchild = createNode(7);
    root->rchild->lchild->lchild = createNode(15);
    root->lchild->lchild->lchild = createNode(4);

    inoder(root);
}
