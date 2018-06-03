//
// Created by Rupesh Choudhary on 8/29/2016.
//

// A C++ program to find sum of all left leaves
#include <iostream>
using namespace std;

struct Node
{
    int key;
    struct Node* left, *right;
};


Node *newNode(char k)
{
    Node *node = new Node;
    node->key = k;
    node->right = node->left = NULL;
    return node;
}

int sum(Node *root)
{
    int res = 0;

    if (root != NULL)
    {
        res = res +root->key;
        if(root->left)
            res = res + sum(root->left);
        if(root->right)
            res = res + sum(root->right);
    }

     return res;
}

int count(Node *root)
{
    if(!root)
        return 0;

    return 1 + count(root->left) + count(root->right);
}

int sum2(Node *root)
{
    if(!root)
        return 0;

    return root->key + sum2(root->left) + sum2(root->right);
}

int main()
{

    struct Node *root         = newNode(20);
    root->left                = newNode(9);
    root->right               = newNode(49);
    root->right->left         = newNode(23);
    root->right->right        = newNode(52);
    root->right->right->left  = newNode(50);
    root->left->left          = newNode(5);
    root->left->right         = newNode(12);
    root->left->right->right  = newNode(12);
    cout << "Sum is " << sum2(root)<<endl;
    cout << "count " << count(root)<<endl;
    return 0;
}

