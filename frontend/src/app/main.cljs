;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) KALEIDOS INC

(ns app.main
  (:require
   [app.common.data.macros :as dm]
   [app.common.logging :as log]
   [app.common.uuid :as uuid]
   [app.config :as cf]
   [app.main.data.events :as ev]
   [app.main.data.users :as du]
   [app.main.data.websocket :as ws]
   [app.main.errors]
   [app.main.features :as feat]
   [app.main.store :as st]
   [app.main.thumbnail-renderer :as tr]
   [app.main.ui :as ui]
   [app.main.ui.alert]
   [app.main.ui.confirm]
   [app.main.ui.css-cursors :as cur]
   [app.main.ui.delete-shared]
   [app.main.ui.modal :refer [modal]]
   [app.main.ui.routes :as rt]
   [app.main.worker :as worker]
   [app.util.dom :as dom]
   [app.util.i18n :as i18n]
   [app.util.theme :as theme]
   [beicon.core :as rx]
   [debug]
   [features]
   [potok.core :as ptk]
   [rumext.v2 :as mf]))

(log/setup! {:app :info})

(when (= :browser cf/target)
  (log/info :message "Welcome to penpot"
            :version (:full cf/version)
            :asserts *assert*
            :build-date cf/build-date
            :public-uri (dm/str cf/public-uri)))

(declare reinit)

(defn init-ui
  []
  (mf/mount (mf/element ui/app) (dom/get-element "app"))
  (mf/mount (mf/element modal)  (dom/get-element "modal")))

(defn initialize
  []
  (ptk/reify ::initialize
    ptk/UpdateEvent
    (update [_ state]
      (assoc state :session-id (uuid/next)))

    ptk/WatchEvent
    (watch [_ _ stream]
      (rx/merge
       (rx/of (ev/initialize)
              (feat/initialize)
              (du/initialize-profile))

       (->> stream
            (rx/filter du/profile-fetched?)
            (rx/take 1)
            (rx/map #(rt/init-routes)))

       (->> stream
            (rx/filter du/profile-fetched?)
            (rx/map deref)
            (rx/filter du/is-authenticated?)
            (rx/take 1)
            (rx/map #(ws/initialize)))))))

(defn ^:export init
  []
  (worker/init!)
  (i18n/init! cf/translations)
  (theme/init! cf/themes)
  (cur/init-styles)
  (tr/init!)
  (init-ui)
  (st/emit! (initialize)))

(defn ^:export reinit
  []
  #_(mf/unmount (dom/get-element "app"))
  #_(mf/unmount (dom/get-element "modal"))
  #_(st/emit! (ev/initialize))
  (init-ui))

(defn ^:dev/after-load after-load
  []
  (reinit))

;; Reload the UI when the language changes
(add-watch
 i18n/locale "locale"
 (fn [_ _ old-value current-value]
   (when (not= old-value current-value)
     (reinit))))

(set! (.-stackTraceLimit js/Error) 50)

