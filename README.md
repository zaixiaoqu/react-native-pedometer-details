# react-native-pedometer-details

react native pedometer, The extension is implemented for Android. I was going to do the same thing on IOS. Fortunately, `react-native-health` realized my idea very well. `react-native-pedometer-details` performs well on Android. If you have problems, you can find me on GitHub. 

## Installation

```sh
npm install react-native-pedometer-details 
# or
yarn add react-native-pedometer-details 
```

## Usage

```js
import PedometerDetails from 'react-native-pedometer-details';

PedometerDetails.requestPermission().then((permissionsStatuses) => {
    if (typeof stateText != 'string' || stateText != 'granted') {
        return;
    }
    PedometerDetails.getDaysSteps(20211211).then(res => {
        // res.day ==> 20211211
        // res.stepCount ==> 100
    });
});

// For more usage, please see
// react-native-pedometer-details/src/API.js
```
### Permissions statuses

Permission checks and requests resolve into one of these statuses:

| Return value          | Notes                                                             |
| --------------------- | ----------------------------------------------------------------- |
| `RESULTS.UNAVAILABLE` | This feature is not available (on this device / in this context)  |
| `RESULTS.DENIED`      | The permission has not been requested / is denied but requestable |
| `RESULTS.GRANTED`     | The permission is granted                                         |
| `RESULTS.LIMITED`     | The permission is granted but with limitations                    |
| `RESULTS.BLOCKED`     | The permission is denied and not requestable anymore              |

## License

MIT
