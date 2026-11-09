"use client";

import { useSyncExternalStore } from "react";

const subscribe = () => () => {};

export function isDesktopDev(): boolean {
  return typeof window !== "undefined" &&
    (window as Window & { __AMS_DESKTOP_DEV__?: boolean }).__AMS_DESKTOP_DEV__ === true;
}

// This marker only changes presentation; it never grants access or native permissions.
export function useDesktopDev(): boolean {
  return useSyncExternalStore(subscribe, isDesktopDev, () => false);
}
