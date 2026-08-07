package ua.stetsenkoinna.examples;

import ua.stetsenkoinna.petriobj.ExceptionInvalidNetStructure;
import ua.stetsenkoinna.petriobj.ExceptionInvalidTimeDelay;
import ua.stetsenkoinna.petriobj.PetriObjModel;
import ua.stetsenkoinna.petriobj.PetriSim;
import ua.stetsenkoinna.libnet.NetLibrary;
import ua.stetsenkoinna.api.dto.DataCollectionConfigDto;
import ua.stetsenkoinna.graphpresentation.statistic.dto.data.StatisticConsoleMonitor;

import java.util.ArrayList;

/**
 * @author Inna V. Stetsenko
 * Example usage of the Statistic module:
 * 1) Define the statistic formula to explore:
 *    1.1) Use functions from the PetriStatFunction enum.
 *    1.2) Combine functions with mathematical operators.
 *    1.3) Specify the petri object to take elements from using "O1." — the number is the
 *         object's position in the model, counted from zero. default: 0
 * 2) Configure data collection:
 *    2.1) Set the start time for data collection. default: 0
 *    2.2) Define the collection step interval. default: 1
 *
 * 3) Assign the monitor to the model object.
 *
 * <p>Standalone runnable example (has its own {@code main}); kept in test sources so it
 * stays out of the production JAR.
 */
public class PetriObjSimulationExample {  //Результати співпадають з аналітичними обрахунками
      public static void main(String[] args) throws ExceptionInvalidTimeDelay, ExceptionInvalidNetStructure {
          // цей фрагмент для запуску імітації моделі з заданною мережею Петрі на інтервалі часу timeModeling
          PetriObjModel model = getModel();

          String formula = "P_AVG(O2.P2)+P_AVG(O3.P2)-T_AVG(O4.T1)";
          DataCollectionConfigDto dataCollectionConfigDto = new DataCollectionConfigDto();
          dataCollectionConfigDto.setDataCollectionStep(10000.0);
          StatisticConsoleMonitor statisticConsoleMonitor = new StatisticConsoleMonitor(formula, dataCollectionConfigDto);
          statisticConsoleMonitor.setIsMonitoringEnabled(true);
          model.setStatisticCollector(statisticConsoleMonitor);

          model.setIsProtokol(false);
          double timeModeling = 1000000;
          model.go(timeModeling);

         //Цей фрагмент для виведення результатів моделювання на консоль
          System.out.println("Mean value of queue");
          for (int j = 1; j < 5; j++) {
              System.out.println(model.getListObj().get(j).getNet().getListP()[0].getMean());
          }
          System.out.println("Mean value of channel worked");
          for (int j = 1; j < 4; j++) {
              System.out.println(1.0 - model.getListObj().get(j).getNet().getListP()[1].getMean());
          }
          System.out.println(2.0 - model.getListObj().get(4).getNet().getListP()[1].getMean());

          System.out.println("Estimation precision");
          double[] valuesQueue = {1.786,0.003,0.004,0.00001};

           System.out.println(" Mean value of queue  precision: ");
           for (int j = 1; j < 5; j++) {
              double inaccuracy = ( model.getListObj().get(j).getNet().getListP()[0].getMean()-valuesQueue[j-1])/valuesQueue[j-1]*100;
              inaccuracy = Math.abs(inaccuracy);
              System.out.println(inaccuracy+" %");
          }

           double[] valuesChannel = {0.714,0.054,0.062,0.036};

           System.out.println(" Mean value of channel worked  precision: ");

           for (int j = 1; j < 4; j++) {
              double inaccuracy = ( 1.0 - model.getListObj().get(j).getNet().getListP()[1].getMean()-valuesChannel[j-1])/valuesChannel[j-1]*100;
             inaccuracy = Math.abs(inaccuracy);

              System.out.println(inaccuracy+" %");
          }
            double inaccuracy = ( 2.0 - model.getListObj().get(4).getNet().getListP()[1].getMean()-valuesChannel[3])/valuesChannel[3]*100;
            inaccuracy = Math.abs(inaccuracy);

           System.out.println(inaccuracy+" %");
      }

      // метод для конструювання моделі масового обслуговування з 4 СМО
      public static PetriObjModel getModel() throws ExceptionInvalidTimeDelay, ExceptionInvalidNetStructure{
          ArrayList<PetriSim> list = new ArrayList<>();
          list.add(new PetriSim(NetLibrary.CreateNetGenerator(2.0)));                  // O0
          list.add(new PetriSim(NetLibrary.CreateNetSMOwithoutQueue(1, 0.6,"First")));  // O1
          list.add(new PetriSim(NetLibrary.CreateNetSMOwithoutQueue(1, 0.3, "Second"))); // O2
          list.add(new PetriSim(NetLibrary.CreateNetSMOwithoutQueue(1, 0.4,"Third")));  // O3
          list.add(new PetriSim(NetLibrary.CreateNetSMOwithoutQueue(2, 0.1,"Forth")));  // O4
          list.add(new PetriSim(NetLibrary.CreateNetFork(0.15, 0.13, 0.3)));            // O5

          PetriObjModel model = new PetriObjModel(list);

          // Composition by shared places: the model records every link, so it can be cloned
          // and written to a document exactly as it is wired here.
          model.linkObjectsCombiningPlaces(0, 1, 1, 0); // gen  => SMO1
          model.linkObjectsCombiningPlaces(1, 2, 5, 0); // SMO1 => fork

          model.linkObjectsCombiningPlaces(5, 1, 2, 0); // fork => SMO2
          model.linkObjectsCombiningPlaces(5, 2, 3, 0); // fork => SMO3
          model.linkObjectsCombiningPlaces(5, 3, 4, 0); // fork => SMO4

          model.linkObjectsCombiningPlaces(2, 2, 1, 0); // SMO2 => SMO1
          model.linkObjectsCombiningPlaces(3, 2, 1, 0); // SMO3 => SMO1
          model.linkObjectsCombiningPlaces(4, 2, 1, 0); // SMO4 => SMO1

          return model;
      }
}
