(ns academic-phrases-web.core
  (:require [academic-phrases-web.phrases :refer [all-phrases]]
            [reagent.core :as reagent :refer [atom]]
            [clojure.string :as s]
            [cljsjs.clipboard]
            [com.rpl.specter :as S :refer-macros [select select-one ALL MAP-VALS collect]]))


(enable-console-print!)

(defonce app-state (atom {:template ""
                          :choice1 ""
                          :choice2 ""
                          :choice3 ""
                          :sentence-id 0
                          :topics []
                          :topic-title ""
                          :section :all}))


(defn replace-placeholder []
  (let [tmp (:template @app-state)
        ch1 (:choice1 @app-state)
        ch2 (:choice2 @app-state)
        ch3 (:choice3 @app-state)]
    (s/replace tmp #"\[\{1\}\]|\[\{2\}\]|\[\{3\}\]"
               {"[{1}]" ch1 "[{2}]" ch2 "[{3}]" ch3})))

(defn get-item-by-id [id]
  (S/select-one (S/walker #(= (:id %) id)) all-phrases))

(defn get-items-by-ids [ids]
  (mapv get-item-by-id ids))

(defn get-ids-by-cats [cats]
  (S/select [(S/submap cats) S/ALL S/ALL :items S/ALL :id] all-phrases))


(defn gen-options-group [idx choices]
  [:select {:on-change #(swap! app-state assoc-in [(keyword (str "choice" (str (inc idx))))] (-> % .-target .-value))}
   [:option "__"]
   (for [choice choices]
     [:option choice])])

(defn get-all-titles []
  (S/select [S/MAP-VALS :title] all-phrases))

(defn get-topic-title [topic-id]
  (let [cat (keyword (str "cat" topic-id))]
    (S/select-one [cat :title] all-phrases)))

(defn get-topic-title-by-cat [cat]
  (S/select-one [cat :title] all-phrases))

(defn get-topics-titles-by-cats [cats]
  (mapv get-topic-title-by-cat cats))

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

(defn clipboard-button [label target]
  (let [clipboard-atom (atom nil)]
    (reagent/create-class
     {:display-name "clipboard-button"
      :component-did-mount
      #(let [clipboard (new js/Clipboard (reagent/dom-node %))]
         (reset! clipboard-atom clipboard))
      :component-will-unmount
      #(when-not (nil? @clipboard-atom)
         (.destroy @clipboard-atom)
         (reset! clipboard-atom nil))
      :reagent-render
      (fn []
        [:button.btn.btn-primary.clipboard
         {:data-clipboard-target target
          :style {:visibility (if (empty? (:choice1 @app-state)) "hidden" "visible")}
          }
         label])})))

(defn sent-ui []
  [:div.animated.fadeIn.text-center
   (dyn-sent (:sentence-id @app-state))
   [:div.empty
    [:h4.animated.fadeIn {:id "copy-this"
                          :style {:visibility (if (empty? (:choice1 @app-state)) "hidden" "visible")}}
     (replace-placeholder)]
    [:div.empty-action
     [clipboard-button "Copy" "#copy-this"]
     ]]
   ])

(defn mark-placeholders [sent]
  (interpose [:mark "__"] (s/split sent #"__")))

(defn sent-card [sent]
  [:table.table.table-hover
   [:tbody
    [:tr.c-hand {:on-click #(do
                              (swap! app-state assoc :sentence-id (:id sent))
                              (swap! app-state assoc :template (:template sent))
                              (reset-choices)
                              (mount-component sent-ui))}
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
      [:div.animated.fadeIn.text-center
       [:h3 title]
       (map
        (fn [t]
          (sent-card t))
        (get-items-by-title title))])))

(defn topic-card [topic]
  [:table.table.table-hover
   [:tbody
    [:tr.c-hand {:on-click #(do
                              (swap! app-state assoc :topic-title topic)
                              (mount-component topic-ui))}
     [:td [:strong topic]]
     [:td
      [:button.btn.btn-primary.float-right
       {:on-click #(do
                     (swap! app-state assoc :topic-title topic)
                     (mount-component topic-ui))} [:i.icon.icon-forward]]
      ]]]
   ])


(defn topics-ui []
  (let [secs {:abstract [:cat1 :cat2 :cat4 :cat5]
              :intro (gen-cats-keywords 1 16)
              :review (conj (gen-cats-keywords 9 16) :cat4)
              :methods (gen-cats-keywords 17 30)
              :results (gen-cats-keywords 29 40)
              :discussion (gen-cats-keywords 35 45)
              :conclusion (gen-cats-keywords 45 51)
              :acknowledgments [:cat52]
              :all (gen-cats-keywords 1 57)}]
    (fn []
      [:div.animated.fadeIn
       [:button.btn.btn-primary {:on-click #(update-topics! (get-all-titles))} "All Topics"]
       [:button.btn.btn-primary {:class (if (empty? (:topics @app-state)) "disabled" "btn-primary")
                                 :on-click #(do
                                              (swap! app-state assoc :topics [])
                                              (swap! app-state assoc :topic-title ""))} "Clear Topics"]
       [:button.btn.btn-primary {:on-click #(mount-component topic-ui)
                                 :class (if (empty? (:topic-title @app-state)) "d-invisible" "d-visible")} (:topic-title @app-state)]
       [:input {:placeholder "Search" :class "form-input" :type "text"
                :on-change (fn [e] (swap! app-state assoc :topics
                                          (into [] (filter (fn [t]
                                                             (s/includes? (s/lower-case t) (-> e .-target .-value)))
                                                           (get-all-titles)))))}]
       (doall
        (update-topics! (get-topics-titles-by-cats ((:section @app-state) secs)))
        [:div (map (fn [t] ^{:key t}[topic-card t]) (:topics @app-state))])])))

(defn gen-cats-keywords [s e]
  (into [] (map #(keyword (str "cat" %)) (range s e))))


(defn sections-ui []
  (let [secs ["All" "Abstract" "Intro" "Review" "Methods" "Results" "Discussion" "Conclusion" "Acknowledgments"]]
    (fn []
      [:table.table.table-hover
       [:tbody
        (for [sec secs]
          [:tr.c-hand
           [:td {:on-click #(do
                              (swap! app-state assoc :section (keyword (s/lower-case sec)))
                              (mount-component topics-ui))}
            [:strong sec]]
           [:td [:button.btn.btn-primary.float-right [:i.icon.icon-forward]]]
           ]
          )
        ]]
      )))

(defn main-ui []
  [:div.container
   [:div.columns
    [:div.column.col-12.col-mx-auto
     [:h2.text-center "Academic Phrases"]
     ]
    ]

   [:div.divider]

   [:div
    [:ul.breadcrumb
     [:li.breadcrumb-item.c-hand [:a {:on-click #(mount-component sections-ui)} "Sections"]]
     (if (= (:topic-title @app-state) "")
       [:li.breadcrumb-item [:a {:href "#"} "No topic selected"]]
       [:li.breadcrumb-item [:a {:href "#"} (:topic-title @app-state)]])
     ]
    ]

   [:div#main-body {:style {:max-"100%"}}]

   [:div.divider]

   [:div.columns.centered
    [:div.navbar
     [:div.column.col-5
      [:section.navbar-section
       [:a.btn.btn-link.tooltip.tooltip-right {:on-click #(mount-component topics-ui)
                                               :data-tooltip "Browse phrases by topics"} "Topics"]

       [:a.btn.btn-link.tooltip.tooltip-right {:on-click #(mount-component topics-ui)
                                               :data-tooltip "Browse phrases by the paper sections"} "Sections"]
       ]
      ]
     [:div.column.col-2
      [:section.navbar-center
       [:img.centered.circle {:src "./img/avatar.jpg" :width "34px"}]
       ]
      ]
     [:div.column.col-5
      [:section.navbar-section.float-right
       [:a.btn.btn-link {:href "https://twitter.com/nashamri"} "Twitter"]
       [:a.btn.btn-link {:href "https://github.com/nashamri/academic-phrases-web"} "GitHub"]
       ]
      ]
     ]
    ]

   ]
  )

(reagent/render-component [main-ui]
                          (. js/document (getElementById "app")))

(reagent/render-component [sections-ui]
                          (. js/document (getElementById "main-body")))

(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
  )
