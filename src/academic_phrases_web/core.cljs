(ns academic-phrases-web.core
  (:require [academic-phrases-web.phrases :refer [all-phrases]]
            [reagent.core :as reagent :refer [atom]]
            [clojure.string :as s]
            [com.rpl.specter :as S :refer-macros [select select-one ALL MAP-VALS collect]]))


(enable-console-print!)

(defonce app-state (atom {:template ""
                          :choice1 ""
                          :choice2 ""
                          :choice3 ""
                          :sentence-id 0
                          :topics []
                          :topic-title ""}))


(defn replace-placeholder []
  (let [tmp (:template @app-state)
        ch1 (:choice1 @app-state)
        ch2 (:choice2 @app-state)
        ch3 (:choice3 @app-state)]
    (s/replace tmp #"\[\{1\}\]|\[\{2\}\]|\[\{3\}\]"
               {"[{1}]" ch1 "[{2}]" ch2 "[{3}]" ch3})))

(defn get-item-by-id [id]
  (S/select-one (S/walker #(= (:id %) id)) all-phrases))

(defn gen-options-group [idx choices]
  [:select {:on-click #(swap! app-state assoc-in [(keyword (str "choice" (str (inc idx))))] (-> % .-target .-value))}
   (for [choice choices]
     [:option choice])])

(defn get-all-titles []
  (S/select [S/MAP-VALS :title] all-phrases))

(defn get-topic-titles [topic-id]
  (S/select [(keyword (str "cat" topic-id)) :items] all-phrases))

(defn get-items-by-title [title]
  (S/select [(S/walker #(= (:title %) title)) :items S/ALL] all-phrases))


(defn update-topics! [titles]
  (swap! app-state assoc :topics titles))

(defn select-html [id]
  (let [chs (:choices (get-item-by-id id))]
    (map-indexed gen-options-group chs)))

(defn dyn-sent [id]
  (let [item (get-item-by-id id)
        template (:template item)
        split-tmp (s/split template #"\[\{1\}\]|\[\{2\}\]|\[\{3\}\]")
        choices (:choices item)
        select (concat (select-html id) " ")
        sentence (-> split-tmp
                     (->> (map (fn [i] [:span i])))
                     (interleave select))]
    sentence))

(defn mount-component [comp]
  (reagent/render-component [comp] (. js/document (getElementById "main-body"))))

(defn reset-choices []
  (do
    (swap! app-state assoc :choice1 "")
    (swap! app-state assoc :choice2 "")
    (swap! app-state assoc :choice3 "")))

(defn sent-ui []
  [:div.animated.fadeIn
   (dyn-sent (:sentence-id @app-state))
   [:h1.animated.fadeIn (replace-placeholder)]
   ])

(defn mark-placeholders [sent]
  (interpose [:mark "__"] (s/split sent #"__")))

(defn sent-card [sent]
  [:table.table
   [:tbody
    [:tr
     [:td (mark-placeholders (s/replace (:template sent) #"\[\{1\}\]|\[\{2\}\]|\[\{3\}\]" "__"))]
     [:td
      [:button.btn.btn-primary.float-right
       {:on-click #(do
                     (swap! app-state assoc :sentence-id (:id sent))
                     (swap! app-state assoc :template (:template sent))
                     (reset-choices)
                     (mount-component sent-ui))} [:i.icon.icon-forward]]
      ]]]
   ])

(defn topic-ui []
  (let [title (:topic-title @app-state)]
    (fn []
      [:div.animated.fadeIn
       [:h3 title]
       (map
        (fn [t]
          (sent-card t))
        (get-items-by-title title))])))

(defn topic-card [topic]
  [:table.table
   [:tbody
    [:tr
     [:td [:strong topic]]
     [:td
      [:button.btn.btn-primary.float-right
       {:on-click #(do
                     (swap! app-state assoc :topic-title topic)
                     (mount-component topic-ui))} [:i.icon.icon-forward]]
      ]]]
   ])


(defn topics-ui []
  [:div.animated.fadeIn
   [:button.btn.btn-primary {:on-click #(update-topics! (get-all-titles))} "All Topics"]
   [:button.btn.btn-primary {:class (if (empty? (:topics @app-state)) "disabled" "btn-primary")
                             :on-click #(do
                                          (swap! app-state assoc :topics [])
                                          (swap! app-state assoc :topic-title ""))} "Clear Topics"]
   [:input {:placeholder "Search" :class "form-input" :type "text"
            :on-change (fn [e] (swap! app-state assoc :topics
                                      (into [] (filter (fn [t]
                                                         (s/includes? (s/lower-case t) (-> e .-target .-value)))
                                                       (get-all-titles)))))}]
   [:div (map (fn [t] ^{:key t}[topic-card t]) (:topics @app-state))]])

(defn main-ui []
  [:div.container
   [:div.columns
    [:button.btn.btn-primary {:on-click #(mount-component topics-ui)} "topics"]
    [:button.btn.btn-primary {:on-click #(mount-component topic-ui)
                              :class (if (empty? (:topic-title @app-state)) "disabled" "btn-primary")}
     "topic"]
    ]
   [:hr]

   [:div#main-body]
   ;; [:hr]
   ;; [:p (str (:topic-title @app-state))]
   ])

(reagent/render-component [main-ui]
                          (. js/document (getElementById "app")))

(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
  )
