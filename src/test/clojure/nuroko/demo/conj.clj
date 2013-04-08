(ns nuroko.demo.conj
  (:use [nuroko.lab core charts])
  (:use [nuroko.gui visual])
  (:use [clojure.core.matrix])
  (:require [task.core :as task])
  (:require [nuroko.data mnist])
  (:import [mikera.vectorz Op])
  (:import [nuroko.coders CharCoder])
  (:import [mikera.vectorz AVector Vectorz]))

(ns nuroko.demo.conj)

(defn demo []

;; ============================================================
;; SCRABBLE score task

	(def scores (sorted-map \a 1,  \b 3 , \c 3,  \d 2,  \e 1,
	                        \f 4,  \g 2,  \h 4,  \i 1,  \j 8,
	                        \k 5,  \l 1,  \m 3,  \n 1,  \o 1,
	                        \p 3,  \q 10, \r 1,  \s 1,  \t 1,
	                        \u 1,  \v 4,  \w 4,  \x 8,  \y 4,
	                        \z 10))
	
	(def score-coder (int-coder :bits 4))
	(encode score-coder 3)
	(decode score-coder *1)
 
	(def letter-coder 
    (class-coder :values (keys scores)))
	(encode letter-coder \c)
	
	(def task 
	  (mapping-task scores 
	                :input-coder letter-coder
	                :output-coder score-coder))
	
	(def net 
	  (neural-network :inputs 26 
	                  :outputs 4
	                  :hidden-sizes [6]))
  
  (show (network-graph net :line-width 2) 
        :title "Neural Net : Scrabble")
 
  (defn scrabble-score [net letter]
    (->> letter
      (encode letter-coder)
      (think net)
      (decode score-coder)))

  (scrabble-score net \a)
  
  
  ;; evaluation function
  (defn evaluate-scores [net]
    (let [net (.clone net)
          chars (keys scores)]
      (count (for [c chars 
         :when (= (scrabble-score net c)
                  (scores c))] c))))  
    
  (show (time-chart 
          [#(evaluate-scores net)] 
          :y-max 26) 
        :title "Correct letters")
   
  ;; training algorithm
  (def trainer (supervised-trainer net task))
  
  (task/run 
    {:sleep 1 :repeat 4000}
    (trainer net))
   
  (scrabble-score net \q)
  
;; end of SCRABBLE DEMO  
  
  
  
;; ============================================================
;; MNIST digit recognistion task

  ;; training data - 60,000 cases
  (def data @nuroko.data.mnist/data-store)
  (def labels @nuroko.data.mnist/label-store)

  (count data)

  ;; some visualisation
  ;; image display function
  (defn img [vector]
    ((image-generator :width 28 :height 28) vector))  
    
  (show (map img (take 100 data)) 
        :title "First 100 digits") 

  ;; we also have some labels  
  (count labels)
  (take 10 labels)
  
  ;; ok so let's compress these images

  (def compress-task (identity-task data)) 
  
  (def compressor 
	  (neural-network :inputs 784 
	                  :outputs 150
                    :layers 1))
  
  (def decompressor 
	  (neural-network :inputs 150  
	                  :outputs 784
                    :layers 1))
  
  (def reconstructor 
    (connect compressor decompressor)) 

 
  (defn show-reconstructions []
    (show 
      (->> (take 100 data)
           (map (partial think reconstructor)) 
           (map img)) 
      :title "100 digits reconstructed"))
  (show-reconstructions) 


  (def trainer (supervised-trainer reconstructor compress-task))
  
	(task/run 
    {:sleep 10 :repeat 100}
    (do 
      (dotimes [i 10] (trainer reconstructor))
      (show-reconstructions)))
    
  (task/stop-all)
 
  ;; look at feature maps for 150 hidden units
  (defn feature-img [vector]
    ((image-generator :width 28 :height 28 :colour-function weight-colour-rgb) vector))  
 
  (show (map feature-img 
             (feature-maps compressor :scale 4)) :title "Feature maps") 

  
  ;; now for the digit recognition
  (def num-coder (class-coder 
                   :values (range 10)))
	(encode num-coder 3)
	
 	(def recognition-task 
	  (mapping-task 
      (apply hash-map 
             (interleave data labels)) 
 	    :output-coder num-coder))
  
  (def recogniser
    (neural-network :inputs 150  
	                  :outputs 10
                    :layers 3))
  
  (def recognition-network 
    (connect compressor recogniser))
  
  (def trainer2 (supervised-trainer recognition-network recognition-task))

  ;; test data and task - 10,000 cases
  (def test-data @nuroko.data.mnist/test-data-store)
  (def test-labels @nuroko.data.mnist/test-label-store)
 
  (def recognition-test-task 
	  (mapping-task (apply hash-map 
                        (interleave test-data test-labels)) 
	                :output-coder num-coder))
  
  (show (time-chart [#(evaluate-classifier 
                        recognition-task recognition-network )
                     #(evaluate-classifier 
                        recognition-test-task recognition-network )] 
                    :y-max 1.0) 
        :title "Error rate")
  
  (task/run 
    {:sleep 5 :repeat 10000}
    (trainer2 recognition-network ))
    
  (task/stop-all)
  
  (defn recognise [image-data]
    (->> image-data
      (think recognition-network)
      (decode num-coder)))

  (recognise (data 0))
  
  (show (map recognise 
             (take 100 data)) 
        :title "Recognition results") 
  
  
  
  ;; ===============================
  ;; END of DEMO
  
  
  
  ;; final view of feature maps
  (task/stop-all)
  
  (show (map feature-img (feature-maps recognition-network :scale 10)) :title "Recognition maps")
  (show (map feature-img (feature-maps reconstructor :scale 10)) :title "Round trip maps") 
)