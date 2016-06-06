//
// Created by Rupesh Choudhary on 6/7/2016.
//


#include<iostream>
#include<stack>

using namespace std;

int getMaxArea(int hist[], int n) {

    stack<int> s;

    int max_area = 0;
    int t_stack;
    int temp_area, wid;

    int i = 0;
    while (i < n) {
        if (s.empty() || hist[s.top()] <= hist[i])
            s.push(i++);
        else {
            t_stack = s.top();
            s.pop();

            if (s.empty())
                wid = i;
            else
                wid = i - s.top() - 1;

            temp_area = hist[t_stack] * wid;

            if (max_area < temp_area)
                max_area = temp_area;
        }
    }

    while (!s.empty()) {
        t_stack = s.top();
        s.pop();

        if (s.empty())
            wid = i;
        else
            wid = i - s.top() - 1;

        temp_area = hist[t_stack] * wid;

        if (max_area < temp_area)
            max_area = temp_area;
    }

    return max_area;
}

int main() {
    int hist[6] = {2, 1, 5, 6, 2, 3};
    cout << "Maximum area is " << getMaxArea(hist, 6);
    return 0;
}