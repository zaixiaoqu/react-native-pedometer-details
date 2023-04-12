import { NativeModules, Platform } from 'react-native';

const { PedometerDetails } = NativeModules;

export default class API {
  /**
   * 根据日期得到步数
   */
  static getDaysSteps(date) {
    if (Platform.OS !== 'android') {
      return new Promise((resolve, reject) => {
        resolve({
          date: 0,
          stepCount: 0
        })
      });
    }
    return PedometerDetails.getDaysSteps(date);
  }

  /**
   * 检索移动数据库中最近的行走时间
   */
  static readLastStepsTime() {
    if (Platform.OS !== 'android') {
      return new Promise((resolve, reject) => {
        resolve(0)
      });
    }
    return PedometerDetails.readLastStepsTime();
  }

  /**
   * 检索移动数据库中最近的行走日期
   */
  static readLastStepsDate() {
    if (Platform.OS !== 'android') {
      return new Promise((resolve, reject) => {
        resolve(0)
      });
    }
    return PedometerDetails.readLastStepsDate();
  }

  /**
   * 请求权限
   */
  static requestPermission() {
    if (Platform.OS !== 'android') {
      return new Promise((resolve, reject) => {
        resolve('blocked')
      });
    }
    return PedometerDetails.requestPermission();
  }

  /**
   * 判断是否有足够的权限
   */
  static isNeedRequestPermission() {
    if (Platform.OS !== 'android') {
      return new Promise((resolve, reject) => {
        resolve('blocked')
      });
    }
    return PedometerDetails.isNeedRequestPermission();
  }

  /**
   * 获取一天中每小时（或一年中的一天、一年中的一周或一年中的一个月）所采取的步骤数
   */

  static getStepsByTimeUnit(
    date,
    timeUnit,
    asc = true,
    weekStart = 2
  ) {
    if (Platform.OS !== 'android') {
      return new Promise((resolve, reject) => {
        resolve([])
      });
    }
    return PedometerDetails.getStepsByTimeUnit(date, timeUnit, asc, weekStart);
  }

  /**
   * 获取一天中每小时（或一年中的一天、一年中的一周或一年中的一个月）所采取的步骤数
   */
  static getSteps(
    timeUnit, theDate, theDay = 0,
    weekStart = 2
  ) {
    if (Platform.OS !== 'android') {
      return new Promise((resolve, reject) => {
        resolve(0)
      });
    }
    return PedometerDetails.getSteps(timeUnit, theDate, theDay, weekStart);
  }
  static restartService() {
    if (Platform.OS !== 'android') {
      return new Promise((resolve, reject) => {
        resolve('')
      });
    }
    return PedometerDetails.restartService();
  }
  /**
   * 获取一天中每小时（或一年中的一天、一年中的一周或一年中的一个月）所采取的步骤数
   */
  static getAverageSteps(
    timeUnit, weekStart = 2
  ) {
    if (Platform.OS !== 'android') {
      return new Promise((resolve, reject) => {
        resolve(0)
      });
    }
    return PedometerDetails.getAverageSteps(timeUnit, weekStart);
  }
}
