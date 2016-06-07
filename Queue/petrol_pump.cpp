//
// Created by Rupesh Choudhary on 6/8/2016.
//
#include<iostream>

using namespace std;

struct petrolPump {
    int petrol;
    int distance;
};

int circleTour(struct petrolPump arr[], int n) {
    int start = 0;
    int end = 1;

    int curr_petrol = arr[start].petrol - arr[start].distance;

    while (end != start) {
        while (curr_petrol < 0 && start != end)  //remove patrol if current patrol is in negative
        {
            curr_petrol -= arr[start].petrol - arr[start].distance;  //remove patrol
            start = (start + 1) % n;                                   //change the starting point

            if (start == 0)   //again we come at starting the no solution
                return -1;
        }

        curr_petrol += arr[end].petrol - arr[end].distance;  //add patrol
        end = (end + 1) % n; //increment array
    }

    return start;
}

int main() {
    struct petrolPump arr[] = {{6, 4},
                               {3, 6},
                               {7, 3}};

    int n = sizeof(arr) / sizeof(arr[0]);
    int start = circleTour(arr, n);

    (start == -1) ? cout << "No solution" : cout << "Start = " << start;

    return 0;
}