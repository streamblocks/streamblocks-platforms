#ifndef __EXELIXI_FIFO_H__
#define __EXELIXI_FIFO_H__

#include <cstring>


/*! \class Fifo fifo.h
 *  \brief A template class that implements a non-bocking ring buffer.
 */
template<typename T>
class Fifo {
public:
	Fifo(int size = 4096, int threshold = 8192, unsigned int nb_readers = 1);
	virtual ~Fifo();

	virtual T* write_address();

	virtual void write_advance();

	virtual void write_advance(unsigned int nb_data);

	virtual T* read_address(int reader_id);

	virtual T* read_address(int reader_id, unsigned nb_data);

	virtual void read_advance(int reader_id, unsigned int nb_data = 1);

	virtual unsigned int count(int reader_id) const {
		return (size + wr_ptr - rd_ptr[reader_id]) & (size - 1);
	}

	virtual unsigned int rooms() const {
		unsigned int min_rooms = 0xFFFFFFFF;
		for (int i = 0; i < nb_readers; i++) {
			unsigned int rooms = (size + rd_ptr[i] - wr_ptr - 1) & (size - 1);
			min_rooms = min_rooms < rooms ? min_rooms : rooms;
		}
		return min_rooms;
	}

protected:
	T * buffer;

	const unsigned int nb_readers;

	unsigned int *rd_ptr;

	unsigned int wr_ptr;

	unsigned int size;
};

template<typename T>
Fifo<T>::Fifo(int size, int threshold, unsigned int nb_readers) :
		buffer(new T[size + threshold]), wr_ptr(0), size(size), nb_readers(nb_readers), rd_ptr( new unsigned int[nb_readers] ) {
	for (int i = 0; i < nb_readers; i++)
		rd_ptr[i] = 0;
}

template<typename T>
Fifo<T>::~Fifo() {
    delete[] rd_ptr;
    rd_ptr = NULL;
	delete[] buffer;
}

template<typename T>
inline T* Fifo<T>::write_address() {
	return buffer + wr_ptr;
}

template<typename T>
void Fifo<T>::write_advance() {
	++wr_ptr;
	wr_ptr &= (size - 1);
}

template<typename T>
void Fifo<T>::write_advance(unsigned int nb_val) {
	int rest = wr_ptr + nb_val - size;
	if (rest > 0) {
		std::memcpy(buffer, buffer + size, rest * sizeof(T));
	}
	wr_ptr += nb_val;
	wr_ptr &= (size - 1);
}

template<typename T>
inline T* Fifo<T>::read_address(int reader_id) {
	return buffer + rd_ptr[reader_id];
}

template<typename T>
inline T* Fifo<T>::read_address(int reader_id, unsigned uNbVal) {
	T * pVal = buffer + rd_ptr[reader_id];
	int rest = rd_ptr[reader_id] + uNbVal - size;
	if (rest > 0) {
		memcpy(buffer + size, buffer, rest * sizeof(T));
	}
	return pVal;
}

template<typename T>
void Fifo<T>::read_advance(int reader_id, unsigned int nb_val) {
	rd_ptr[reader_id] += nb_val;
	rd_ptr[reader_id] &= (size - 1);
}

#endif // __EXELIXI_FIFO_H__
