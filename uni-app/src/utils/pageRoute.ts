export interface UniPageWithRouteOptions {
  route?: string
  options?: Record<string, string | undefined>
  $page?: {
    options?: Record<string, string | undefined>
  }
}

export function getCurrentPageRouteOptions(): Record<string, string | undefined> {
  const pages = getCurrentPages()
  const currentPage = pages.length > 0 ? (pages[pages.length - 1] as UniPageWithRouteOptions) : null
  return currentPage?.$page?.options || currentPage?.options || {}
}
