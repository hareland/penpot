;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) KALEIDOS INC

(ns app.util.code-gen.style-css
  (:require
   [app.common.data :as d]
   [app.common.data.macros :as dm]
   [app.common.pages.helpers :as cph]
   [app.common.text :as txt]
   [app.main.ui.shapes.text.styles :as sts]
   [app.util.code-gen.common :as cgc]
   [app.util.code-gen.style-css-formats :refer [format-value]]
   [app.util.code-gen.style-css-values :refer [get-value]]
   [cuerdas.core :as str]))

;;
;; Common styles to display always. Will be attached as a prelude to the generated CSS
;;
(def prelude "
html, body {
  background-color: #E8E9EA;
  margin: 0;
  min-height: 100%;
  min-width: 100%;
  padding: 0;
}

body {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 2rem;
  gap: 2rem;
}

svg {
  position: absolute;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);
}

* {
  box-sizing: border-box;
}

.text-node { background-clip: text !important; -webkit-background-clip: text !important; }

")

(def shape-css-properties
  [:position
   :left
   :top
   :width
   :height
   :transform
   :background
   :background-color
   :background-image
   :border
   :border-radius
   :box-shadow
   :filter
   :opacity
   :overflow

   ;; Flex/grid related properties
   :display
   :align-items
   :align-content
   :justify-items
   :justify-content
   :gap
   :column-gap
   :row-gap
   :padding

   ;; Flex related properties
   :flex-direction
   :flex-wrap

   ;; Grid related properties
   :grid-template-rows
   :grid-template-columns

   ;; Flex/grid self properties
   :flex-shrink
   :margin
   :max-height
   :min-height
   :max-width
   :min-width
   :align-self
   :justify-self

   ;; Grid cell properties
   :grid-column
   :grid-row
   ])

(def text-node-css-properties
  [:font-family
   :font-style
   :font-size
   :font-weight
   :line-height
   :letter-spacing
   :text-decoration
   :text-transform
   :color])

(defn shape->css-property
  [shape objects property]
  (when-let [value (get-value property shape objects)]
    [property value]))

(defn shape->css-properties
  "Given a shape extract the CSS properties in the format of list [property value]"
  [shape objects properties]
  (->> properties
       (keep (fn [property]
               (when-let [value (get-value property shape objects)]
                 [property value])))))

(defn format-css-value
  ([[property value] options]
   (format-css-value property value options))

  ([property value options]
   (when (some? value)
     (format-value property value options))))

(defn format-css-property
  [[property value] options]
  (when (some? value)
    (let [formatted-value (format-css-value property value options)]
      (dm/fmt "%: %;" (d/name property) formatted-value))))

(defn format-css-properties
  "Format a list of [property value] into a list of css properties in the format 'property: value;'"
  [properties options]
  (->> properties
       (map #(dm/str "  " (format-css-property % options)))
       (str/join "\n")))

(defn get-shape-properties-css
  ([objects shape properties]
   (get-shape-properties-css objects shape properties nil))

  ([objects shape properties options]
   (-> shape
       (shape->css-properties objects properties)
       (format-css-properties options))))

(defn format-js-styles
  [properties _options]
  (format-css-properties
   (->> (.keys js/Object properties)
        (remove #(str/starts-with? % "--"))
        (mapv (fn [key]
                [(str/kebab key) (unchecked-get properties key)])))
   nil))

(defn node->css
  [shape shape-selector node]
  (let [properties
        (case (:type node)
          (:root "root")
          (sts/generate-root-styles shape node)

          (:paragraph-set "paragraph-set")
          (sts/generate-paragraph-set-styles shape)

          (:paragraph "paragraph")
          (sts/generate-paragraph-styles shape node)

          (sts/generate-text-styles shape node))]
    (dm/fmt
     ".% {\n%\n}"
     (dm/str shape-selector " ." (:$id node))
     (format-js-styles properties nil))))

(defn generate-text-css
  [shape]
  (let [selector (cgc/shape->selector shape)]
    (->> shape
         :content
         (txt/index-content)
         (txt/node-seq)
         (map #(node->css shape selector %))
         (str/join "\n"))))

(defn get-shape-css-selector
  ([objects shape]
   (get-shape-css-selector shape objects nil))

  ([shape objects options]
   (let [properties (-> shape
                        (shape->css-properties objects shape-css-properties)
                        (format-css-properties options))
         selector (cgc/shape->selector shape)]
     (str/join "\n" [(str/fmt "/* %s */" (:name shape))
                     (str/fmt ".%s {\n%s\n}" selector properties)
                     (when (cph/text-shape? shape) (generate-text-css shape))]))))

(defn get-css-property
  ([objects shape property]
   (get-css-property objects shape property nil))

  ([objects shape property options]
   (-> shape
       (shape->css-property objects property)
       (format-css-property options))))

(defn get-css-value
  ([objects shape property]
   (get-css-value objects shape property nil))

  ([objects shape property options]
   (when-let [prop (shape->css-property shape objects property)]
     (format-css-value prop options))))

(defn generate-style
  ([objects shapes]
   (generate-style objects shapes nil))
  ([objects shapes options]
   (dm/str
    prelude
    (->> shapes
         (map #(get-shape-css-selector % objects options))
         (str/join "\n\n")))))
