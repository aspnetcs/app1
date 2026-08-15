export type RecordingCapability = {
  supported: boolean
  reason: string
}

export function getRecordingCapability(): RecordingCapability {
  if (typeof uni === 'undefined' || typeof uni.getRecorderManager !== 'function') {
    return {
      supported: false,
      reason: '当前环境不支持录音能力',
    }
  }

  if (typeof window !== 'undefined') {
    const mediaDevices = window.navigator?.mediaDevices
    if (!mediaDevices || typeof mediaDevices.getUserMedia !== 'function') {
      return {
        supported: false,
        reason: '当前浏览器无法访问麦克风，请检查浏览器权限',
      }
    }
  }

  return {
    supported: true,
    reason: '',
  }
}

export function getRecordingUnsupportedMessage(capability = getRecordingCapability()): string {
  return capability.reason || '当前环境不支持录音能力'
}
