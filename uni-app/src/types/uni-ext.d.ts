/**
 * Extended uni-app type declarations for DCloud-specific APIs
 * that are not in the standard @dcloudio/types package.
 */

/** WeChat mini-program FileSystemManager (subset used in this project) */
interface UniFileSystemManager {
  readFile(options: {
    filePath: string
    encoding?: string
    success?: (res: { data: string | ArrayBuffer }) => void
    fail?: (err: { errMsg: string }) => void
  }): void
}

/** Extend global uni namespace with DCloud-specific APIs */
declare namespace UniApp {
  interface Uni {
    /** Available on WeChat mini-program runtime */
    getFileSystemManager?(): UniFileSystemManager
    env?: {
      USER_DATA_PATH?: string
    }
  }
}

/** WeChat global namespace (available in wx mini-program runtime) */
interface WxGlobal {
  getFileSystemManager?(): UniFileSystemManager
  [key: string]: unknown
}

declare var wx: WxGlobal | undefined

/** Extend globalThis for browser/mini-program cross-platform access */
interface UniGlobalThis {
  document?: Document
  navigator?: Navigator
  screen?: Screen
  wx?: WxGlobal
}
